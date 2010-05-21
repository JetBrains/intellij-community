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
package com.intellij.execution.impl;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditorConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.TabbedPaneWrapper;

import javax.swing.*;

/**
 * @author dyoma
 */
class TypeTemplatesConfigurable implements Configurable {
  private final ConfigurationType myType;
  private final Configurable[] myConfigurables;
  private TabbedPaneWrapper myTabbedPane;

  public TypeTemplatesConfigurable(final ConfigurationType type, final RunManagerImpl runManager, Project project) {
    myType = type;
    myConfigurables = new Configurable[getFactories().length];
    myTabbedPane = new TabbedPaneWrapper(project);
    final ConfigurationFactory[] factories = getFactories();
    for (int i = 0; i < factories.length; i++) {
      final ConfigurationFactory factory = factories[i];
      final RunnerAndConfigurationSettings template = runManager.getConfigurationTemplate(factory);
      final Configurable configurable = new TemplateConfigurable(template);
      myConfigurables[i] = configurable;
      myTabbedPane.addTab(factory.getName(), configurable.getIcon(), configurable.createComponent(), null);
    }
  }

  private ConfigurationFactory[] getFactories() {
    return myType.getConfigurationFactories();
  }

  public String getDisplayName() {
    return ExecutionBundle.message("template.settings.configurable.display.name");
  }

  public Icon getIcon() {
    return myType.getIcon();
  }

  public String getHelpTopic() {
    final int index = myTabbedPane.getSelectedIndex();
    return myConfigurables[index].getHelpTopic();
  }

  public JComponent createComponent() {
    return myTabbedPane.getComponent();
  }

  public boolean isModified() {
    for (final Configurable configurable : myConfigurables) {
      if (configurable.isModified()) return true;
    }
    return false;
  }

  public void apply() throws ConfigurationException {
    for (final Configurable configurable : myConfigurables) {
      configurable.apply();
    }
  }

  public void reset() {
    for (final Configurable configurable : myConfigurables) {
      configurable.reset();
    }
  }

  public void disposeUIResources() {
    for (int i = 0; i < myConfigurables.length; i++) {
      final Configurable configurable = myConfigurables[i];
      configurable.disposeUIResources();
      myConfigurables[i] = null;
    }
    myTabbedPane = null;
  }

  public static Configurable createConfigurable(final ConfigurationType type, final Project project) {
    final ConfigurationFactory[] factories = type.getConfigurationFactories();
    final RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(project);
    return factories.length == 1
           ? new TemplateConfigurable(runManager.getConfigurationTemplate(factories[0]))
           : new TypeTemplatesConfigurable(type, runManager, project);
  }

  private static class TemplateConfigurable extends SettingsEditorConfigurable<RunnerAndConfigurationSettings> {
    private final RunnerAndConfigurationSettings myTemplate;

    public TemplateConfigurable(RunnerAndConfigurationSettings template) {
      super(new ConfigurationSettingsEditorWrapper(template), template);
      myTemplate = template;
    }

    public String getDisplayName() {
      return myTemplate.getConfiguration().getName();
    }

    public Icon getIcon() {
      return myTemplate.getConfiguration().getType().getIcon();
    }

    public String getHelpTopic() {
      return null;
    }
  }
}
