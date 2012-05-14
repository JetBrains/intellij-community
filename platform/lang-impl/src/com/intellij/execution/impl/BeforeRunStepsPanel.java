/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.UnknownRunConfiguration;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.containers.hash.HashSet;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Vassiliy Kudryashov
 */
class BeforeRunStepsPanel extends JPanel {
  private JCheckBox myShowSettingsBeforeRunCheckBox;
  private JBList myList;
  private final CollectionListModel<BeforeRunTask> myModel;
  private RunConfiguration myRunConfiguration;

  private final List<BeforeRunTask> originalTasks = new ArrayList<BeforeRunTask>();
  private StepsBeforeRunListener myListener;

  BeforeRunStepsPanel(StepsBeforeRunListener listener) {
    myListener = listener;
    myModel = new CollectionListModel<BeforeRunTask>();
    myList = new JBList(myModel);
    myList.getEmptyText().setText(ExecutionBundle.message("before.launch.panel.empty"));
    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myList.setCellRenderer(new MyListCellRenderer());
    ToolbarDecorator myDecorator = ToolbarDecorator.createDecorator(myList).setVisibleRowCount(4);
    if (!SystemInfo.isMac) {
      myDecorator.setAsTopToolbar();
    }
    myDecorator.setEditAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        int index = myList.getSelectedIndex();
        if (index ==-1)
          return;
        Pair<BeforeRunTask, BeforeRunTaskProvider<BeforeRunTask>> selection = getSelection();
        if (selection == null)
          return;
        BeforeRunTask task = selection.getFirst();
        BeforeRunTaskProvider<BeforeRunTask> provider = selection.getSecond();
        if (provider.configureTask(myRunConfiguration, task)) {
          myModel.setElementAt(task, index);
        }
      }
    });
    myDecorator.setEditActionUpdater(new AnActionButtonUpdater() {
      @Override
      public boolean isEnabled(AnActionEvent e) {
        Pair<BeforeRunTask, BeforeRunTaskProvider<BeforeRunTask>> selection = getSelection();
        return selection != null && selection.getSecond().isConfigurable();
      }
    });
    myDecorator.setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        doAddAction(button);
      }
    });

    myShowSettingsBeforeRunCheckBox = new JCheckBox(ExecutionBundle.message("configuration.edit.before.run"));

    setLayout(new MigLayout("fill, ins 0, gap 10"));
    add(myShowSettingsBeforeRunCheckBox, "shrinky, wrap");
    add(myDecorator.createPanel(), "grow, push");
  }

  @Nullable
  private Pair<BeforeRunTask, BeforeRunTaskProvider<BeforeRunTask>> getSelection() {
    final int index = myList.getSelectedIndex();
    if (index ==-1)
      return null;
    BeforeRunTask task = myModel.getElementAt(index);
    Key providerId = task.getProviderId();
    BeforeRunTaskProvider<BeforeRunTask> provider = BeforeRunTaskProvider.getProvider(myRunConfiguration.getProject(), providerId);
    return provider != null ? Pair.create(task, provider) : null;
  }

  void doReset(RunnerAndConfigurationSettings settings) {
    myRunConfiguration = settings.getConfiguration();

    originalTasks.clear();
    originalTasks.addAll(RunManagerImpl.getInstanceImpl(myRunConfiguration.getProject()).getBeforeRunTasks(myRunConfiguration));
    myModel.replaceAll(originalTasks);
    myShowSettingsBeforeRunCheckBox.setSelected(settings.isEditBeforeRun());
    myShowSettingsBeforeRunCheckBox.setEnabled(!(myRunConfiguration instanceof UnknownRunConfiguration));
  }

  public List<BeforeRunTask> getTasks(boolean applyCurrentState) {
    if (applyCurrentState) {
      originalTasks.clear();
      originalTasks.addAll(myModel.getItems());
    }
    return Collections.unmodifiableList(originalTasks);
  }

  public boolean needEditBeforeRun() {
    return myShowSettingsBeforeRunCheckBox.isSelected();
  }

  void doAddAction(AnActionButton button) {
      if (myRunConfiguration instanceof UnknownRunConfiguration) {
        return;
      }

      final JBPopupFactory popupFactory = JBPopupFactory.getInstance();
      final BeforeRunTaskProvider<BeforeRunTask>[] providers = Extensions.getExtensions(BeforeRunTaskProvider.EXTENSION_POINT_NAME,
                                                                                        myRunConfiguration.getProject());
    final List<Key> activeProviderKeys = getActiveProviderKeys();

    DefaultActionGroup actionGroup = new DefaultActionGroup(null, false);
      for (final BeforeRunTaskProvider<BeforeRunTask> provider : providers) {
        String providerName = provider.getName();
        if (provider.createTask(myRunConfiguration) == null)
          continue;
        if (activeProviderKeys.contains(provider.getId()) && provider.isSingleton())
          continue;
        AnAction providerAction = new AnAction(providerName, null, provider.getIcon()) {
          @Override
          public void actionPerformed(AnActionEvent e) {
            BeforeRunTask task = provider.createTask(myRunConfiguration);
            if (task != null) {
              provider.configureTask(myRunConfiguration, task);
              if (!provider.canExecuteTask(myRunConfiguration, task))
                return;
            } else {
              return;
            }
            task.setEnabled(true);

            Set<RunConfiguration> configurationSet = new HashSet<RunConfiguration>();
            getAllRunBeforeRuns(task, configurationSet);
            if (configurationSet.contains(myRunConfiguration)) {
              JOptionPane.showMessageDialog(BeforeRunStepsPanel.this,
                                            ExecutionBundle.message("before.launch.panel.cyclic_dependency_warning",
                                                                    myRunConfiguration.getName(),
                                                                    provider.getDescription(task)),
                                            ExecutionBundle.message("warning.common.title"),JOptionPane.WARNING_MESSAGE);
              return;
            }
            myModel.add(task);
            myListener.fireStepsBeforeRunChanged();
          }
        };
        actionGroup.add(providerAction);
      }
      final ListPopup popup =
        popupFactory.createActionGroupPopup(ExecutionBundle.message("add.new.run.configuration.acrtion.name"), actionGroup,
                                            SimpleDataContext.getProjectContext(myRunConfiguration.getProject()), false, false, false, null,
                                            -1, Condition.TRUE);
      popup.show(button.getPreferredPopupPoint());
  }

  private List<Key> getActiveProviderKeys() {
    List<Key> result = new ArrayList<Key>();
    for (BeforeRunTask task : myModel.getItems()) {
      result.add(task.getProviderId());
    }
    return result;
  }

  private void getAllRunBeforeRuns(BeforeRunTask task, Set<RunConfiguration> configurationSet) {
    if (task instanceof RunConfigurationBeforeRunProvider.RunConfigurableBeforeRunTask) {
      RunConfigurationBeforeRunProvider.RunConfigurableBeforeRunTask runTask
        = (RunConfigurationBeforeRunProvider.RunConfigurableBeforeRunTask)task;
      RunConfiguration configuration = runTask.getSettings().getConfiguration();

      List<BeforeRunTask> tasks = RunManagerImpl.getInstanceImpl(configuration.getProject()).getBeforeRunTasks(configuration);
      for (BeforeRunTask beforeRunTask : tasks) {
        if (beforeRunTask instanceof RunConfigurationBeforeRunProvider.RunConfigurableBeforeRunTask) {
          if (configurationSet.add(((RunConfigurationBeforeRunProvider.RunConfigurableBeforeRunTask)beforeRunTask).getSettings().getConfiguration()))
            getAllRunBeforeRuns(beforeRunTask, configurationSet);
        }
      }
    }
  }

  interface StepsBeforeRunListener {
    void fireStepsBeforeRunChanged();
  }

  private class MyListCellRenderer extends JBList.StripedListCellRenderer {
    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      if (value instanceof BeforeRunTask) {
        BeforeRunTask task = (BeforeRunTask)value;
        BeforeRunTaskProvider<BeforeRunTask> provider = BeforeRunTaskProvider.getProvider(myRunConfiguration.getProject(), task.getProviderId());
        if (provider != null) {
          setIcon(provider.getTaskIcon(task));
          setText(provider.getDescription(task));
        }
      }
      return this;
    }
  }
}
