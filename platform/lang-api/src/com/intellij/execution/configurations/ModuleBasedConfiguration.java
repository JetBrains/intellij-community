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

package com.intellij.execution.configurations;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.ArrayUtil;
import com.intellij.util.xmlb.annotations.Property;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Base class for a configuration that is associated with a specific module. For example, Java run configurations use the selected module
 * to determine the run classpath.
 */
public abstract class ModuleBasedConfiguration<ConfigurationModule extends RunConfigurationModule> extends LocatableConfigurationBase implements Cloneable, ModuleRunConfiguration {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.configurations.ModuleBasedConfiguration");

  @Property(surroundWithTag = false)
  private final ConfigurationModule myModule;

  @NonNls
  protected static final String TO_CLONE_ELEMENT_NAME = "toClone";

  public ModuleBasedConfiguration(final String name,
                                  @NotNull ConfigurationModule configurationModule, @NotNull ConfigurationFactory factory) {
    super(configurationModule.getProject(), factory, name);
    myModule = configurationModule;
  }

  public ModuleBasedConfiguration(final ConfigurationModule configurationModule, final ConfigurationFactory factory) {
    super(configurationModule.getProject(), factory, "");
    myModule = configurationModule;
  }

  public abstract Collection<Module> getValidModules();

  public ConfigurationModule getConfigurationModule() {
    return myModule;
  }

  public void setModule(final Module module) {
    myModule.setModule(module);
  }

  public void setModuleName(@Nullable String moduleName) {
    myModule.setModuleName(moduleName);
  }

  protected void readModule(final Element element) {
    myModule.readExternal(element);
  }

  protected void writeModule(@NotNull Element element) {
    //if (myModule.getModule() != null) {
      myModule.writeExternal(element);
    //}
  }

  public Collection<Module> getAllModules() {
    return Arrays.asList(ModuleManager.getInstance(getProject()).getModules());
  }

  /**
   * @deprecated  method {@link com.intellij.execution.configurations.ConfigurationFactory#createTemplateConfiguration(com.intellij.openapi.project.Project)}
   * would be used instead to avoid wrong custom 'cloning'
   */
  protected ModuleBasedConfiguration createInstance() {
    ModuleBasedConfiguration<ConfigurationModule> configuration =
      (ModuleBasedConfiguration<ConfigurationModule>)getFactory().createTemplateConfiguration(getProject());
    configuration.setName(getName());
    return configuration;
  }

  @Override
  public ModuleBasedConfiguration clone() {
    final Element element = new Element(TO_CLONE_ELEMENT_NAME);
    try {
      writeExternal(element);
      RunConfiguration configuration = getFactory().createTemplateConfiguration(getProject());
      configuration.setName(getName());
      configuration.readExternal(element);
      return (ModuleBasedConfiguration)configuration;
    }
    catch (InvalidDataException | WriteExternalException e) {
      LOG.error(e);
      return null;
    }
  }

  @Override
  @NotNull
  public Module[] getModules() {
    Module module = ReadAction.compute(() -> getConfigurationModule().getModule());
    return module == null ? Module.EMPTY_ARRAY : new Module[] {module};
  }

  public void restoreOriginalModule(final Module originalModule) {
    if (canRestoreOriginalModule(originalModule, getModules())) {
      setModule(originalModule);
    }
  }

  public static boolean canRestoreOriginalModule(Module originalModule, Module[] configModules) {
    if (originalModule == null || configModules.length == 0) {
      return false;
    }

    Deque<Module> queue = new ArrayDeque<>();
    queue.addLast(originalModule);
    Set<Module> modules = new THashSet<>();
    while (!queue.isEmpty()) {
      Module module = queue.removeFirst();
      //configModules contains 1 element
      if (ArrayUtil.contains(module, configModules)) {
        return true;
      }

      for (Module next : ModuleRootManager.getInstance(module).getModuleDependencies(true)) {
        if (!modules.add(next)) continue;
        queue.addLast(next);
      }
    }
    return false;
  }

  public void onNewConfigurationCreated() {
    final RunConfigurationModule configurationModule = getConfigurationModule();
    if (configurationModule.getModule() == null) {
      final Module[] modules = ModuleManager.getInstance(getProject()).getModules();
      configurationModule.setModule(modules.length == 1 ? modules[0] : null);
    }
  }

  public boolean isModuleDirMacroSupported() {
    return false;
  }
}
