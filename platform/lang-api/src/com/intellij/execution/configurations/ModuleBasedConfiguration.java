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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.xmlb.annotations.Property;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

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

  protected void readModule(final Element element) throws InvalidDataException {
    myModule.readExternal(element);
  }

  protected void writeModule(final Element element) throws WriteExternalException {
    myModule.writeExternal(element);
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
    } catch (InvalidDataException e) {
      LOG.error(e);
      return null;
    } catch (WriteExternalException e) {
      LOG.error(e);
      return null;
    }
  }

  @Override
  @NotNull
  public Module[] getModules() {
    return ApplicationManager.getApplication().runReadAction(new Computable<Module[]>() {
      @Override
      @SuppressWarnings({"ConstantConditions"})
      public Module[] compute() {
        final Module module = getConfigurationModule().getModule();
        return module == null ? Module.EMPTY_ARRAY : new Module[] {module};
      }
    });
  }

  public void restoreOriginalModule(final Module originalModule) {
    if (originalModule == null) return;
    final Module[] classModules = getModules();
    final Set<Module> modules = new HashSet<Module>();
    for (Module classModule : classModules) {
      ModuleUtilCore.collectModulesDependsOn(classModule, modules);
    }
    if (modules.contains(originalModule)) setModule(originalModule);
  }

  public void onNewConfigurationCreated() {
    final RunConfigurationModule configurationModule = getConfigurationModule();
    if (configurationModule.getModule() == null) {
      final Module[] modules = ModuleManager.getInstance(getProject()).getModules();
      configurationModule.setModule(modules.length == 1 ? modules[0] : null);
    }
  }
}
