/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.compound;

import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunConfigurationBeforeRunProvider;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.ui.CheckBoxList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

public class CompoundRunConfigurationSettingsEditor extends SettingsEditor<CompoundRunConfiguration>{
  @NotNull private final Project myProject;
  private final CheckBoxList<RunConfiguration> myList;
  private List<RunConfiguration> myChecked = new ArrayList<RunConfiguration>();
  private final RunManagerImpl myRunManager;


  public CompoundRunConfigurationSettingsEditor(@NotNull Project project) {
    myProject = project;
    myRunManager = RunManagerImpl.getInstanceImpl(myProject);
    myList = new CheckBoxList<RunConfiguration>() {
      @Override
      protected void adjustRendering(JCheckBox checkBox, int index, boolean selected, boolean hasFocus) {
        RunConfiguration configuration = getItemAt(index);
        assert configuration != null;
        checkBox.setText(configuration.getType().getDisplayName() + " '"+configuration.getName()+"'");
      }
    };
    myList.setVisibleRowCount(100);
  }

  private void updateModel(@NotNull CompoundRunConfiguration s) {
    List<RunConfiguration> list = myRunManager.getAllConfigurationsList();
    Collections.sort(list, CompoundRunConfiguration.COMPARATOR);
    for (RunConfiguration configuration : list) {
      if (canBeAdded(configuration, s)) {
        myList.addItem(configuration, configuration.getName(), myChecked.contains(configuration));
      }
    }
  }

  private  boolean canBeAdded(@NotNull RunConfiguration candidate, @NotNull final CompoundRunConfiguration root) {
    if (candidate == root) return false;
    List<BeforeRunTask> tasks = myRunManager.getBeforeRunTasks(candidate);
    for (BeforeRunTask task : tasks) {
      if (task instanceof RunConfigurationBeforeRunProvider.RunConfigurableBeforeRunTask) {
        RunConfigurationBeforeRunProvider.RunConfigurableBeforeRunTask runTask
          = (RunConfigurationBeforeRunProvider.RunConfigurableBeforeRunTask)task;
        RunnerAndConfigurationSettings settings = runTask.getSettings();
        if (settings != null) {
         if (!canBeAdded(settings.getConfiguration(), root)) return false;
        }
      }
    }
    if (candidate instanceof CompoundRunConfiguration) {
      Set<RunConfiguration> set = ((CompoundRunConfiguration)candidate).getSetToRun();
      for (RunConfiguration configuration : set) {
        if (!canBeAdded(configuration, root)) return false;
      }
    }
    return true;
  }

  @Override
  protected void resetEditorFrom(CompoundRunConfiguration s) {
    myChecked.clear();
    myChecked.addAll(s.getSetToRun());
    updateModel(s);
  }

  @Override
  protected void applyEditorTo(CompoundRunConfiguration s) throws ConfigurationException {
    Set<RunConfiguration> checked = new HashSet<RunConfiguration>();
    for (int i = 0; i < myList.getItemsCount(); i++) {
      RunConfiguration configuration = myList.getItemAt(i);
      if (myList.isItemSelected(configuration)) {
        checked.add(configuration);
      }
    }
    Set<RunConfiguration> toRun = s.getSetToRun();
    toRun.clear();
    toRun.addAll(checked);
  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    return myList;//new JBScrollPane(myList, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
  }
}
