/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.execution.configurations;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public abstract class ConfigurationTypeBase implements ConfigurationType {
  private static final ConfigurationFactory[] EMPTY_FACTORIES = new ConfigurationFactory[0];

  private final String myId;
  private final String myDisplayName;
  private final String myDescription;
  private final Icon myIcon;
  private ConfigurationFactory[] myFactories;

  protected ConfigurationTypeBase(@NotNull String id, String displayName, String description, Icon icon) {
    myId = id;
    myDisplayName = displayName;
    myDescription = description;
    myIcon = icon;
    myFactories = EMPTY_FACTORIES;
  }

  protected void addFactory(ConfigurationFactory factory) {
    List<ConfigurationFactory> newFactories = new ArrayList<ConfigurationFactory>(myFactories.length + 1);
    Collections.addAll(newFactories, myFactories);
    newFactories.add(factory);
    myFactories = newFactories.toArray(new ConfigurationFactory[newFactories.size()]);
  }

  public String getDisplayName() {
    return myDisplayName;
  }

  public String getConfigurationTypeDescription() {
    return myDescription;
  }

  public Icon getIcon() {
    return myIcon;
  }

  @NotNull
  public String getId() {
    return myId;
  }

  public ConfigurationFactory[] getConfigurationFactories() {
    return myFactories;
  }
}
