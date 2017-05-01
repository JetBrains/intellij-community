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
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.stream.Collectors;

@State(name = "ModuleRunConfigurationManager")
public final class ModuleRunConfigurationManager implements PersistentStateComponent<Element> {
  private static final String SHARED = "shared";
  private static final String LOCAL = "local";
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
          myManager.removeConfigurations(getModuleRunConfigurationSettings());
        }
      }
    });
  }

  @Nullable
  @Override
  public Element getState() {
    try {
      return new Element("state")
        .addContent(writeExternal(new Element(SHARED), true))
        .addContent(writeExternal(new Element(LOCAL), false))
        ;
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

  public Element writeExternal(@NotNull final Element element, boolean isShared) throws WriteExternalException {
    LOG.debug("writeExternal(" + myModule + "); shared: " + isShared);
    myManager.writeConfigurations(
      element,
      getModuleRunConfigurationSettings().stream()
        .filter(settings -> settings.isShared() == isShared)
        .collect(Collectors.toList())
    );
    return element;
  }

  public void readExternal(@NotNull final Element element) {
    Element sharedElement = element.getChild(SHARED);
    if (sharedElement != null) {
      doReadExternal(sharedElement, true);
    }
    Element localElement = element.getChild(LOCAL);
    if (localElement != null) {
      doReadExternal(localElement, false);
    }
  }

  private void doReadExternal(@NotNull Element element, boolean isShared) {
    LOG.debug("readExternal(" + myModule + ");  shared: " + isShared);

    for (final Element child : element.getChildren(RunManagerImpl.CONFIGURATION)) {
      myManager.loadConfiguration(child, isShared);
    }

    myManager.requestSort();
  }
}
