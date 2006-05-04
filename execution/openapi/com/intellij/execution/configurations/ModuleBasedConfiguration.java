/*
 * Copyright 2000-2006 JetBrains s.r.o.
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
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.Arrays;
import java.util.Collection;

public abstract class ModuleBasedConfiguration extends RuntimeConfiguration {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.configurations.ModuleBasedConfiguration");
  private final RunConfigurationModule myModule;
  @NonNls
  protected static final String TO_CLONE_ELEMENT_NAME = "toClone";

  public ModuleBasedConfiguration(final String name,
                                  final RunConfigurationModule configurationModule, final ConfigurationFactory factory) {
    super(name, configurationModule.getProject(), factory);
    myModule = configurationModule;
  }

  public abstract Collection<Module> getValidModules();

  public void setModuleName(final String moduleName) {
    myModule.setModuleName(moduleName);
  }

  public RunConfigurationModule getConfigurationModule() {
    return myModule;
  }

  public void init() {
    myModule.init();
  }

  public void setModule(final Module module) {
    if (module == null) return;
    myModule.setModule(module);
  }

  public void readExternal(Element element) throws InvalidDataException{
    super.readExternal(element);
  }
  public void writeExternal(Element element) throws WriteExternalException{
    super.writeExternal(element);
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

  protected abstract ModuleBasedConfiguration createInstance();

  public ModuleBasedConfiguration clone() {
    final Element element = new Element(TO_CLONE_ELEMENT_NAME);
    try {
      writeExternal(element);
      final ModuleBasedConfiguration configuration = createInstance();
      configuration.init();
      configuration.readExternal(element);
      return configuration;
    } catch (InvalidDataException e) {
      LOG.error(e);
      return null;
    } catch (WriteExternalException e) {
      LOG.error(e);
      return null;
    }
  }

  public Module[] getModules() {
    return ApplicationManager.getApplication().runReadAction(new Computable<Module[]>() {
      public Module[] compute() {
        final Module module = getConfigurationModule().getModule();
        return module == null ? null : new Module[] {module};
      }
    });
  }
}
