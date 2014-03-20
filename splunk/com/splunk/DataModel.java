/*
 * Copyright 2014 Splunk, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"): you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.splunk;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * DataModel represents a data model on the server. Data models contain
 * data model objects, which specify structured views on Splunk data.
 */
public class DataModel extends Entity {
    private final static JsonParser jsonParser = new JsonParser();
    private final static Gson gson = new Gson();

    private final String modelNameLabel = "modelName";
    private final String displayNameLabel = "displayName";
    private final String rawJsonLabel = "description";

    private String description = null;

    private Map<String, DataModelObject> objects;
    private boolean accelerationEnabled;
    private String earliestAcceleratedTime;
    private String accelerationCronSchedule;

    public DataModel(Service service, String path) {
        super(service, path);
        objects = new HashMap<String, DataModelObject>();
    }

    /**
     * Returns whether there is an object of the given name in this data model.
     *
     * @param name Name of the object to check for.
     * @return true if there is an object with that name; false otherwise.
     */
    public boolean containsObject(String name) {
        return this.objects.containsKey(name);
    }

    /**
     * Retrieve an object by name from this data model.
     *
     * @param name Name of the object to retrieve.
     * @return a DataModelObject if there is such an object; null otherwise.
     */
    public DataModelObject getObject(String name) {
        return this.objects.get(name);
    }

    /**
     * @return a collection of all objects in this data model.
     */
    public Collection<DataModelObject> getObjects() {
        return objects.values();
    }

    /**
     * Returns the tsidx namespace which holds global acceleration events for this
     * data model. The namespace will be returned whether acceleration is enabled
     * or not.
     *
     * @return The tsidx namespace for global acceleration of this data model.
     */
    public String getAccelerationNamespace() {
        // For the moment, the acceleration namespace for global acceleration of
        // data models is the name of the data model.
        return getName();
    }

    /**
     * @return whether global acceleration is enabled for this data model.
     */
    public boolean isAccelerated() {
        return this.accelerationEnabled;
    }

    /**
     * @return A human readable description of this data model.
     */
    public String getDescription() {
        return this.description;
    }

    /**
     * @return The raw JSON describing this data model and its objects.
     */
    public String getRawJson() {
        return getString(rawJsonLabel);
    }

    /**
     * @return the human readable name of this data model.
     */
    public String getDisplayName() {
        return getString(displayNameLabel);
    }

    @Override
    public Entity load(AtomObject value) {
        Entity result = super.load(value);
        // After loading the Atom entity as we would for any other Splunk entity,
        // we have to parse the JSON description of the data model and its acceleration
        // status.
        parseDescription(getString(rawJsonLabel));
        parseAcceleration(getString("acceleration"));
        return result;
    }

    /**
     * Parse the JSON returned from splunkd describing this data model.
     *
     * This method writes the results into fields of this object.
     *
     * @param input a String containing JSON.
     */
    void parseDescription(String input) {
        objects = new HashMap<String, DataModelObject>();

        JsonElement rootElement = jsonParser.parse(input);

        for (Entry<String, JsonElement> entry : rootElement.getAsJsonObject().entrySet()) {
            if (entry.getKey().equals(modelNameLabel)) {
                content.put(modelNameLabel, entry.getValue().getAsString());
            } else if (entry.getKey().equals(displayNameLabel)) {
                content.put(displayNameLabel, entry.getValue().getAsString());
            } else if (entry.getKey().equals(rawJsonLabel)) {
                description = entry.getValue().getAsString();
            } else if (entry.getKey().equals("objects")) {
                JsonArray objectArray = entry.getValue().getAsJsonArray();
                for (JsonElement object : objectArray) {
                    DataModelObject dmo = DataModelObject.parse(this, object);
                    objects.put(dmo.getName(), dmo);
                }
            }
        }
    }

    /**
     * Parse the acceleration description from splunkd of this data model.
     *
     * This method writes the results into fields of this object.
     *
     * @param input a string containing JSON.
     */
    public void parseAcceleration(String input) {
         JsonElement rootElement = jsonParser.parse(input);

        for (Entry<String, JsonElement> entry : rootElement. getAsJsonObject().entrySet()) {
            if (entry.getKey().equals("enabled")) {
                accelerationEnabled = entry.getValue().getAsBoolean();
            } else if (entry.getKey().equals("earliest_time")) {
                earliestAcceleratedTime = entry.getValue().getAsString();
            } else if (entry.getKey().equals("cron_schedule")) {
                accelerationCronSchedule = entry.getValue().getAsString();
            }
        }
    }

    /**
     * Enable or disable global acceleration on this data model.
     *
     * @param enabled true enabled, false disables.
     */
    public void setAcceleration(boolean enabled) {
        this.accelerationEnabled = enabled;
        toUpdate.put("enabled", enabled);
    }

    /**
     * Return the earliest time of the window over which the data model is accelerated.
     *
     * Times are represented relative to now, given by a minus sign, a number, and a
     * suffix indicating the time unit (e.g., "-2mon", "-1day").
     *
     * @return a string representing the earliest accelerated time.
     */
    public String getEarliestAcceleratedTime() {
        return earliestAcceleratedTime;
    }

    /**
     * Set the size of the window (from the specified earliest time to now) over
     * which the data model should be accelerated.
     *
     * Times are represented relative to now, given by a minus sign, a number, and a
     * suffix indicating the time unit (e.g., "-2mon", "-1day").
     *
     * @param earliestAcceleratedTime a string specifying a time.
     */
    public void setEarliestAcceleratedTime(String earliestAcceleratedTime) {
        this.earliestAcceleratedTime = earliestAcceleratedTime;
        toUpdate.put("earliest_time", earliestAcceleratedTime);
    }

    /**
     * Return the cron schedule on which the cached data for acceleration should be
     * updated.
     *
     * @return a string containing a crontab style schedule specification.
     */
    public String getAccelerationCronSchedule() {
        return accelerationCronSchedule;
    }

    /**
     * Set the cron schedule on which the cached data for the acceleration should
     * be updated.
     *
     * @param accelerationCronSchedule a crontab style schedule to use.
     */
    public void setAccelerationCronSchedule(String accelerationCronSchedule) {
        this.accelerationCronSchedule = accelerationCronSchedule;
        toUpdate.put("cron_schedule", accelerationCronSchedule);
    }

    @Override
    public void update() {
        // We have to do some munging on the acceleration fields to pass them as JSON
        // to the server.
        Map<String, Object> accelerationMap = new HashMap<String, Object>();
        for (String key : new String[] {"enabled", "earliest_time", "cron_schedule"}) {
            if (toUpdate.containsKey(key)) {
                accelerationMap.put(key, toUpdate.get(key));
                toUpdate.remove(key);
            }
        }
        toUpdate.put("acceleration", gson.toJson(accelerationMap));

        // Now update like we would any other entity.
        super.update();
    }
}
