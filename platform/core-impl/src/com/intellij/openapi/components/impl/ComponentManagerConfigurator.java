/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.components.impl;

import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.extensions.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class ComponentManagerConfigurator {
  private final ComponentManagerImpl myComponentManager;

  public ComponentManagerConfigurator(@NotNull ComponentManagerImpl componentManager) {
    myComponentManager = componentManager;
  }

  private void loadSingleConfig(@NotNull ComponentConfig config,
                                @Nullable PluginDescriptor descriptor,
                                boolean defaultProject) {
    if (defaultProject && !config.isLoadForDefaultProject()) return;
    if (!myComponentManager.isComponentSuitable(config.options)) return;

    myComponentManager.registerComponent(config, descriptor);
  }

  void loadComponentsConfiguration(@NotNull ComponentConfig[] components,
                                   @Nullable PluginDescriptor descriptor,
                                   final boolean defaultProject) {
    for (ComponentConfig config : components) {
      loadSingleConfig(config, descriptor, defaultProject);
    }
  }
}
