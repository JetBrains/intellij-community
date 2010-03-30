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
package com.intellij.debugger.settings;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.classFilter.ClassFilterEditor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class DebuggerSteppingConfigurable implements SearchableConfigurable {
  private JCheckBox myCbStepInfoFiltersEnabled;
  private JCheckBox myCbSkipSyntheticMethods;
  private JCheckBox myCbSkipConstructors;
  private JCheckBox myCbSkipClassLoaders;
  private ClassFilterEditor mySteppingFilterEditor;
  private JCheckBox myCbSkipSimpleGetters;
  private final Project myProject;

  public DebuggerSteppingConfigurable(Project project) {
    myProject = project;
  }

  public void reset() {
    final DebuggerSettings settings = DebuggerSettings.getInstance();
    myCbSkipSimpleGetters.setSelected(settings.SKIP_GETTERS);
    myCbSkipSyntheticMethods.setSelected(settings.SKIP_SYNTHETIC_METHODS);
    myCbSkipConstructors.setSelected(settings.SKIP_CONSTRUCTORS);
    myCbSkipClassLoaders.setSelected(settings.SKIP_CLASSLOADERS);

    myCbStepInfoFiltersEnabled.setSelected(settings.TRACING_FILTERS_ENABLED);

    mySteppingFilterEditor.setFilters(settings.getSteppingFilters());
    mySteppingFilterEditor.setEnabled(settings.TRACING_FILTERS_ENABLED);
  }

  public void apply() {
    getSettingsTo(DebuggerSettings.getInstance());
  }

  private void getSettingsTo(DebuggerSettings settings) {
    settings.SKIP_GETTERS = myCbSkipSimpleGetters.isSelected();
    settings.SKIP_SYNTHETIC_METHODS = myCbSkipSyntheticMethods.isSelected();
    settings.SKIP_CONSTRUCTORS = myCbSkipConstructors.isSelected();
    settings.SKIP_CLASSLOADERS = myCbSkipClassLoaders.isSelected();
    settings.TRACING_FILTERS_ENABLED = myCbStepInfoFiltersEnabled.isSelected();

    mySteppingFilterEditor.stopEditing();
    settings.setSteppingFilters(mySteppingFilterEditor.getFilters());
  }

  public boolean isModified() {
    final DebuggerSettings currentSettings = DebuggerSettings.getInstance();
    final DebuggerSettings debuggerSettings = currentSettings.clone();
    getSettingsTo(debuggerSettings);
    return !debuggerSettings.equals(currentSettings);
  }

  public String getDisplayName() {
    return DebuggerBundle.message("debugger.stepping.configurable.display.name");
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return "reference.idesettings.debugger.stepping";
  }

  public String getId() {
    return getHelpTopic();
  }

  public Runnable enableSearch(String option) {
    return null;
  }

  public JComponent createComponent() {
    final JPanel panel = new JPanel(new GridBagLayout());

    myCbSkipSyntheticMethods = new JCheckBox(DebuggerBundle.message("label.debugger.general.configurable.skip.synthetic.methods"));
    myCbSkipConstructors = new JCheckBox(DebuggerBundle.message("label.debugger.general.configurable.skip.constructors"));
    myCbSkipClassLoaders = new JCheckBox(DebuggerBundle.message("label.debugger.general.configurable.skip.classloaders"));
    myCbSkipSimpleGetters = new JCheckBox(DebuggerBundle.message("label.debugger.general.configurable.skip.simple.getters"));
    myCbStepInfoFiltersEnabled = new JCheckBox(DebuggerBundle.message("label.debugger.general.configurable.step.filters.list.header"));
    panel.add(myCbSkipSyntheticMethods, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 4, 0, 0),0, 0));
    panel.add(myCbSkipConstructors, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 4, 0, 0),0, 0));
    panel.add(myCbSkipClassLoaders, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 4, 0, 0),0, 0));
    panel.add(myCbSkipSimpleGetters, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(0, 4, 0, 0),0, 0));
    panel.add(myCbStepInfoFiltersEnabled, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.WEST, GridBagConstraints.NONE, new Insets(8, 4, 0, 0),0, 0));

    mySteppingFilterEditor = new ClassFilterEditor(myProject);
    panel.add(mySteppingFilterEditor, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(0, 12, 0, 0),0, 0));

    myCbStepInfoFiltersEnabled.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        mySteppingFilterEditor.setEnabled(myCbStepInfoFiltersEnabled.isSelected());
      }
    });
    return panel;
  }

  public void disposeUIResources() {
  }

}
