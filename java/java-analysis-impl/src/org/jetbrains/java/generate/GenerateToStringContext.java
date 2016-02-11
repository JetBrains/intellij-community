/*
 * Copyright 2001-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.java.generate;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.java.generate.config.Config;

/**
 * Application context for this plugin.
 */
@State(name = "ToStringSettings", storages = @Storage("other.xml"))
public class GenerateToStringContext implements PersistentStateComponent<Config> {
    public static GenerateToStringContext getInstance() {
        return ServiceManager.getService(GenerateToStringContext.class);
    }

    private Config config = new Config();

    public static Config getConfig() {
        return getInstance().config;
    }

    public static void setConfig(Config newConfig) {
        getInstance().config = newConfig;
    }

    public Config getState() {
        return config;
    }

    public void loadState(Config state) {
        config = state;
    }
}
