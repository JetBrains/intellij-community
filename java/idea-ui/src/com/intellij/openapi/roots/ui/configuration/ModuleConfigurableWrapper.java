/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.module.ModuleConfigurationEditor;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;

import javax.swing.*;

/**
* @author nik
*/
class ModuleConfigurableWrapper implements ModuleConfigurationEditor {
  private final Configurable myModuleConfigurable;

  public ModuleConfigurableWrapper(Configurable moduleConfigurable) {
    myModuleConfigurable = moduleConfigurable;
  }

  @Override
  public void saveData() {

  }

  @Override
  public void moduleStateChanged() {
  }

  @Override
  public String getDisplayName() {
    return myModuleConfigurable.getDisplayName();
  }

  @Override
  public String getHelpTopic() {
    return myModuleConfigurable.getHelpTopic();
  }

  @Override
  public JComponent createComponent() {
    return myModuleConfigurable.createComponent();
  }

  @Override
  public boolean isModified() {
    return myModuleConfigurable.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    myModuleConfigurable.apply();
  }

  @Override
  public void reset() {
    myModuleConfigurable.reset();
  }

  @Override
  public void disposeUIResources() {
    myModuleConfigurable.disposeUIResources();
  }
}
