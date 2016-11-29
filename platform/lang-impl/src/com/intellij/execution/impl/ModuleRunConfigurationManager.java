/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.module.ModuleComponent;
import com.intellij.openapi.project.ModuleAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@State(name = ModuleRunConfigurationManager.COMPONENT_NAME)
public final class ModuleRunConfigurationManager extends ModuleAdapter implements ModuleComponent, PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance(ModuleRunConfigurationManager.class);
  @NonNls static final String COMPONENT_NAME = "ModuleRunConfigurationManager";
  @NotNull
  private final Condition<RunnerAndConfigurationSettings> myModuleConfigCondition =
    settings -> settings != null && usesMyModule(settings.getConfiguration());
  @NotNull
  private final Module myModule;
  @NotNull
  private final RunManagerImpl myManager;
  @Nullable
  private List<Element> myUnloadedElements = null;

  public ModuleRunConfigurationManager(@NotNull final Module module, @NotNull final RunManagerImpl runManager) {
    myModule = module;
    myManager = runManager;
  }

  @Override
  public void projectOpened() {
  }

  @Override
  public void projectClosed() {
  }

  @Override
  public void moduleAdded() {
  }

  @Override
  public void initComponent() {
    LOG.debug("initComponent(" + myModule + ")");
    myModule.getMessageBus().connect(myModule).subscribe(ProjectTopics.MODULES, this);
  }

  @Override
  public void disposeComponent() {
  }

  @NotNull
  @Override
  public String getComponentName() {
    return COMPONENT_NAME;
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
    return ContainerUtil.filter(myManager.getConfigurationSettings(), myModuleConfigCondition);
  }

  private boolean usesMyModule(RunConfiguration config) {
    return config instanceof ModuleBasedConfiguration
           && myModule.equals(((ModuleBasedConfiguration)config).getConfigurationModule().getModule());
  }

  public void writeExternal(@NotNull final Element element) throws WriteExternalException {
    LOG.debug("writeExternal(" + myModule + ")");
    for (final RunnerAndConfigurationSettings settings : getModuleRunConfigurationSettings()) {
      myManager.addConfigurationElement(element, settings);
    }
    if (myUnloadedElements != null) {
      for (final Element unloadedElement : myUnloadedElements) {
        element.addContent(unloadedElement.clone());
      }
    }
  }

  public void readExternal(@NotNull final Element element) throws InvalidDataException {
    LOG.debug("readExternal(" + myModule + ")");
    myUnloadedElements = null;
    final Set<String> existing = new HashSet<>();

    final List children = element.getChildren();
    for (final Object child : children) {
      final RunnerAndConfigurationSettings configuration = myManager.loadConfiguration((Element)child, true);
      if (configuration == null && Comparing.strEqual(element.getName(), RunManagerImpl.CONFIGURATION)) {
        if (myUnloadedElements == null) myUnloadedElements = new ArrayList<>(2);
        myUnloadedElements.add(element);
      }

      if (configuration != null) {
        existing.add(configuration.getUniqueID());
      }
    }

    for (final RunConfiguration configuration : myManager.getAllConfigurationsList()) {
      if (!usesMyModule(configuration)) {
        RunnerAndConfigurationSettings settings = myManager.getSettings(configuration);
        if (settings != null)
          existing.add(settings.getUniqueID());
      }
    }
    myManager.removeNotExistingSharedConfigurations(existing);

    // IDEA-60004: configs may never be sorted before write, so call it manually after shared configs read
    myManager.setOrdered(false);
    myManager.getSortedConfigurations();
  }

  @Override
  public void beforeModuleRemoved(@NotNull Project project, @NotNull Module module) {
    if (myModule.equals(module)) {
      LOG.debug("time to remove something from project (" + project + ")");
      for (final RunnerAndConfigurationSettings settings : getModuleRunConfigurationSettings()) {
        myManager.removeConfiguration(settings);
      }
    }
  }
}
