/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.execution.impl;

import com.intellij.ProjectTopics;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SmartHashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

@State(name = "ModuleRunConfigurationManager")
public final class ModuleRunConfigurationManager implements PersistentStateComponent<Element> {
  private static final Object LOCK = new Object();
  private static final Logger LOG = Logger.getInstance(ModuleRunConfigurationManager.class);
  @NotNull
  private final Module myModule;
  @NotNull
  private final Condition<RunnerAndConfigurationSettings> myModuleConfigCondition =
    settings -> settings != null && usesMyModule(settings.getConfiguration());
  @NotNull
  private final RunManagerImpl myManager;

  public ModuleRunConfigurationManager(@NotNull final Module module, @NotNull final RunManagerImpl runManager) {
    myModule = module;
    myManager = runManager;

    myModule.getMessageBus().connect().subscribe(ProjectTopics.MODULES, new ModuleListener() {
      @Override
      public void beforeModuleRemoved(@NotNull Project project, @NotNull Module module) {
        if (myModule.equals(module)) {
          LOG.debug("time to remove something from project (" + project + ")");
          Collection<? extends RunnerAndConfigurationSettings> moduleRunConfigurations;
          synchronized (LOCK) {
            moduleRunConfigurations = getModuleRunConfigurationSettings();
          }
          myManager.removeConfigurations(moduleRunConfigurations);
        }
      }
    });
  }

  @Nullable
  @Override
  public Element getState() {
    try {
      final Element e = new Element("state");
      writeExternal(e);
      return e;
    }
    catch (WriteExternalException e1) {
      LOG.error(e1);
      return null;
    }
  }

  @Override
  public void loadState(Element state) {
    try {
      readExternal(state);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  @NotNull
  private Collection<? extends RunnerAndConfigurationSettings> getModuleRunConfigurationSettings() {
    return ContainerUtil.filter(myManager.getAllSettings(), myModuleConfigCondition);
  }

  private boolean usesMyModule(RunConfiguration config) {
    return config instanceof ModuleBasedConfiguration
           && myModule.equals(((ModuleBasedConfiguration)config).getConfigurationModule().getModule());
  }

  public void writeExternal(@NotNull final Element element) throws WriteExternalException {
    LOG.debug("writeExternal(" + myModule + ")");
    myManager.writeConfigurations(element, getModuleRunConfigurationSettings());
  }

  public void readExternal(@NotNull final Element element) {
    synchronized (LOCK) {
      doReadExternal(element);
    }
  }

  private void doReadExternal(@NotNull Element element) {
    LOG.debug("readExternal(" + myModule + ")");
    final Set<String> existing = new SmartHashSet<>();

    for (final Element child : element.getChildren(RunManagerImpl.CONFIGURATION)) {
      existing.add(myManager.loadConfiguration(child, true).getUniqueID());
    }

    for (RunnerAndConfigurationSettings settings : myManager.getAllSettings()) {
      if (!usesMyModule(settings.getConfiguration())) {
        existing.add(settings.getUniqueID());
      }
    }

    myManager.removeNotExistingSharedConfigurations(existing);
    myManager.requestSort();
  }
}
