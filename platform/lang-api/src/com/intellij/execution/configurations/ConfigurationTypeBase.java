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

import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

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

  protected ConfigurationTypeBase(@NotNull @NonNls String id, @Nls String displayName, @Nls String description, Icon icon) {
    myId = id;
    myDisplayName = displayName;
    myDescription = description;
    myIcon = icon;
    myFactories = EMPTY_FACTORIES;
  }

  protected void addFactory(ConfigurationFactory factory) {
    myFactories = ArrayUtil.append(myFactories, factory);
  }

  @Override
  @Nls
  public String getDisplayName() {
    return myDisplayName;
  }

  @Override
  @Nls
  public String getConfigurationTypeDescription() {
    return myDescription;
  }

  @Override
  public Icon getIcon() {
    return myIcon;
  }

  @Override
  @NotNull
  @NonNls
  public String getId() {
    return myId;
  }

  @Override
  public ConfigurationFactory[] getConfigurationFactories() {
    return myFactories;
  }
}
