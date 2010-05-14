/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.execution.ui;

import com.intellij.execution.configurations.JavaRunConfigurationModule;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.ui.ComboboxSpeedSearch;
import com.intellij.ui.SortedComboBoxModel;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class ConfigurationModuleSelector {
  private final Project myProject;
  private final JComboBox myModulesList;
  private final SortedComboBoxModel<Object> myModules = new SortedComboBoxModel<Object>(new Comparator<Object>() {
    public int compare(final Object module, final Object module1) {
      if (module instanceof Module && module1 instanceof Module){
        return ((Module)module).getName().compareToIgnoreCase(((Module)module1).getName());
      }
      return -1;
    }
  });
  private static final String NO_MODULE = "<no module>";

  public ConfigurationModuleSelector(final Project project, final JComboBox modulesList) {
    myProject = project;
    myModulesList = modulesList;
    new ComboboxSpeedSearch(modulesList){
      protected String getElementText(Object element) {
        if (element instanceof Module){
          return ((Module)element).getName();
        } else if (element == null) {
          return NO_MODULE;
        }
        return super.getElementText(element);
      }
    };
    myModulesList.setModel(myModules);
    myModulesList.setRenderer(new DefaultListCellRenderer(){
      public Component getListCellRendererComponent(final JList list, final Object value, final int index, final boolean isSelected, final boolean cellHasFocus) {
        final Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value instanceof Module) {
          final Module module = (Module)value;
          setIcon(module.getModuleType().getNodeIcon(true));
          setText(module.getName());
        } else if (value == null) {
          setText(NO_MODULE);
        }
        return component;
      }
    });
  }

  public void applyTo(final ModuleBasedConfiguration configurationModule) {
    configurationModule.setModule((Module)myModulesList.getSelectedItem());
  }

  public void reset(final ModuleBasedConfiguration configuration) {
    final Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    final List<Module> list = new ArrayList<Module>();
    for (final Module module : modules) {
      if (isModuleAccepted(module)) list.add(module);
    }
    setModules(list);
    myModules.setSelectedItem(configuration.getConfigurationModule().getModule());
  }

  public static boolean isModuleAccepted(final Module module) {
    return ModuleTypeManager.getInstance().isClasspathProvider(module.getModuleType());    
  }

  public Project getProject() {
    return myProject;
  }

  public JavaRunConfigurationModule getConfigurationModule() {
    final JavaRunConfigurationModule configurationModule = new JavaRunConfigurationModule(getProject(), false);
    configurationModule.setModule((Module)myModules.getSelectedItem());
    return configurationModule;
  }

  private void setModules(final Collection<Module> modules) {
    myModules.clear();
    myModules.add(null);
    for (Module module : modules) {
      myModules.add(module);
    }
  }

  public Module getModule() {
    return (Module)myModules.getSelectedItem();
  }

  @Nullable
  public PsiClass findClass(final String className) {
    return getConfigurationModule().findClass(className);
  }

  public String getModuleName() {
    final Module module = (Module)myModules.getSelectedItem();
    return module == null ? "" : module.getName();
  }
}
