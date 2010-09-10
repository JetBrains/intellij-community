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
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleComponent;
import com.intellij.openapi.module.ModuleConfigurationEditor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ModuleConfigurableEP;

import javax.swing.*;
import java.util.ArrayList;

public class ModuleLevelConfigurablesEditorProvider implements ModuleConfigurationEditorProvider, ModuleComponent {
  private static final ExtensionPointName<ModuleConfigurableEP> MODULE_CONFIGURABLES = ExtensionPointName.create("com.intellij.moduleConfigurable");

  private final Module myModule;

  public ModuleLevelConfigurablesEditorProvider(Module module) {
    myModule = module;
  }

  public ModuleConfigurationEditor[] createEditors(ModuleConfigurationState state) {
    ArrayList<ModuleConfigurationEditor> result = new ArrayList<ModuleConfigurationEditor>();
    Configurable[] moduleConfigurables = myModule.getComponents(Configurable.class);
    for (final Configurable moduleConfigurable : moduleConfigurables) {
      result.add(new ConfigurableWrapper(moduleConfigurable));
    }
    final ModuleConfigurableEP[] extensions = myModule.getExtensions(MODULE_CONFIGURABLES);
    for(ModuleConfigurableEP extension : extensions) {
      result.add(new ConfigurableWrapper(extension.createConfigurable()));
    }

    return result.toArray(new ModuleConfigurationEditor[result.size()]);
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public void moduleAdded() {
  }

  public String getComponentName() {
    return "ModuleLevelConfigurablesEditorProvider";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  private static class ConfigurableWrapper implements ModuleConfigurationEditor {
    private final Configurable myModuleConfigurable;

    public ConfigurableWrapper(Configurable moduleConfigurable) {
      myModuleConfigurable = moduleConfigurable;
    }

    public void saveData() {

    }

    public void moduleStateChanged() {
    }

    public String getDisplayName() {
      return myModuleConfigurable.getDisplayName();
    }

    public Icon getIcon() {
      return myModuleConfigurable.getIcon();
    }

    public String getHelpTopic() {
      return myModuleConfigurable.getHelpTopic();
    }

    public JComponent createComponent() {
      return myModuleConfigurable.createComponent();
    }

    public boolean isModified() {
      return myModuleConfigurable.isModified();
    }

    public void apply() throws ConfigurationException {
      myModuleConfigurable.apply();
    }

    public void reset() {
      myModuleConfigurable.reset();
    }

    public void disposeUIResources() {
      myModuleConfigurable.disposeUIResources();
    }
  }
}
