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
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.util.containers.hash.HashSet;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

/**
 * @author Vassiliy Kudryashov
 */
class BeforeRunStepsPanel extends JPanel {

  private final JCheckBox myShowSettingsBeforeRunCheckBox;
  private final JCheckBox myActivateToolWindowBeforeRunCheckBox;
  private final JBList myList;
  private final CollectionListModel<BeforeRunTask> myModel;
  private RunConfiguration myRunConfiguration;

  private final List<BeforeRunTask> originalTasks = new ArrayList<>();
  private final StepsBeforeRunListener myListener;
  private final JPanel myPanel;

  BeforeRunStepsPanel(StepsBeforeRunListener listener) {
    myListener = listener;
    myModel = new CollectionListModel<>();
    myList = new JBList(myModel);
    myList.getEmptyText().setText(ExecutionBundle.message("before.launch.panel.empty"));
    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myList.setCellRenderer(new MyListCellRenderer());

    myModel.addListDataListener(new ListDataListener() {
      @Override
      public void intervalAdded(ListDataEvent e) {
        adjustVisibleRowCount();
        updateText();
      }

      @Override
      public void intervalRemoved(ListDataEvent e) {
        adjustVisibleRowCount();
        updateText();
      }

      @Override
      public void contentsChanged(ListDataEvent e) {
      }

      private void adjustVisibleRowCount() {
        myList.setVisibleRowCount(Math.max(4, Math.min(8, myModel.getSize())));
      }
    });

    ToolbarDecorator myDecorator = ToolbarDecorator.createDecorator(myList);
    if (!SystemInfo.isMac) {
      myDecorator.setAsUsualTopToolbar();
    }
    myDecorator.setEditAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        int index = myList.getSelectedIndex();
        if (index == -1)
          return;
        Pair<BeforeRunTask, BeforeRunTaskProvider<BeforeRunTask>> selection = getSelection();
        if (selection == null)
          return;
        BeforeRunTask task = selection.getFirst();
        BeforeRunTaskProvider<BeforeRunTask> provider = selection.getSecond();
        if (provider.configureTask(myRunConfiguration, task)) {
          myModel.setElementAt(task, index);
          updateText();
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
    myDecorator.setAddActionUpdater(new AnActionButtonUpdater() {
      @Override
      public boolean isEnabled(AnActionEvent e) {
        return checkBeforeRunTasksAbility(true);
      }
    });

    myShowSettingsBeforeRunCheckBox = new JCheckBox(ExecutionBundle.message("configuration.edit.before.run"));
    myShowSettingsBeforeRunCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateText();
      }
    });
    myActivateToolWindowBeforeRunCheckBox = new JCheckBox(ExecutionBundle.message("configuration.activate.toolwindow.before.run"));
    myActivateToolWindowBeforeRunCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateText();
      }
    });

    myPanel = myDecorator.createPanel();

    setLayout(new BorderLayout());
    add(myPanel, BorderLayout.CENTER);
    JPanel checkboxPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, JBUI.scale(5), JBUI.scale(5)));
    checkboxPanel.add(myShowSettingsBeforeRunCheckBox);
    checkboxPanel.add(myActivateToolWindowBeforeRunCheckBox);
    add(checkboxPanel, BorderLayout.SOUTH);
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
    RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(myRunConfiguration.getProject());
    originalTasks.addAll(runManager.getBeforeRunTasks(myRunConfiguration));
    myModel.replaceAll(originalTasks);
    myShowSettingsBeforeRunCheckBox.setSelected(settings.isEditBeforeRun());
    myShowSettingsBeforeRunCheckBox.setEnabled(!isUnknown());
    myActivateToolWindowBeforeRunCheckBox.setSelected(settings.isActivateToolWindowBeforeRun());
    myActivateToolWindowBeforeRunCheckBox.setEnabled(!isUnknown());
    myPanel.setVisible(checkBeforeRunTasksAbility(false));
    updateText();
  }

  private void updateText() {
    StringBuilder sb = new StringBuilder();

    if (myShowSettingsBeforeRunCheckBox.isSelected()) {
      sb.append(ExecutionBundle.message("configuration.edit.before.run"));
    }

    List<BeforeRunTask> tasks = myModel.getItems();
    if (!tasks.isEmpty()) {
      LinkedHashMap<BeforeRunTaskProvider, Integer> counter = new LinkedHashMap<>();
      for (BeforeRunTask task : tasks) {
        BeforeRunTaskProvider<BeforeRunTask> provider =
          BeforeRunTaskProvider.getProvider(myRunConfiguration.getProject(), task.getProviderId());
        if (provider != null) {
          Integer count = counter.get(provider);
          if (count == null) {
            count = task.getItemsCount();
          } else {
            count+=task.getItemsCount();
          }
          counter.put(provider, count);
        }
      }
      for (Iterator<Map.Entry<BeforeRunTaskProvider, Integer>> iterator = counter.entrySet().iterator(); iterator.hasNext(); ) {
        Map.Entry<BeforeRunTaskProvider, Integer> entry = iterator.next();
        BeforeRunTaskProvider provider = entry.getKey();
        String name = provider.getName();
        name = StringUtil.trimStart(name, "Run ");
        if (sb.length() > 0) {
          sb.append(", ");
        }
        sb.append(name);
        if (entry.getValue() > 1) {
          sb.append(" (").append(entry.getValue().intValue()).append(")");
        }
      }
    }
    if (myActivateToolWindowBeforeRunCheckBox.isSelected()) {
      sb.append(sb.length() > 0 ? ", " : "").append(ExecutionBundle.message("configuration.activate.toolwindow.before.run"));
    }
    if (sb.length() > 0) {
      sb.insert(0, ": ");
    }
    sb.insert(0, ExecutionBundle.message("before.launch.panel.title"));
    myListener.titleChanged(sb.toString());
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

  public boolean needActivateToolWindowBeforeRun() {
    return myActivateToolWindowBeforeRunCheckBox.isSelected();
  }

  private boolean checkBeforeRunTasksAbility(boolean checkOnlyAddAction) {
    if (isUnknown()) {
      return false;
    }
    Set<Key> activeProviderKeys = getActiveProviderKeys();
    final BeforeRunTaskProvider<BeforeRunTask>[] providers = Extensions.getExtensions(BeforeRunTaskProvider.EXTENSION_POINT_NAME,
                                                                                      myRunConfiguration.getProject());
    for (final BeforeRunTaskProvider<BeforeRunTask> provider : providers) {
      if (provider.createTask(myRunConfiguration) != null) {
        if (!checkOnlyAddAction) {
          return true;
        }
        else if (!provider.isSingleton() || !activeProviderKeys.contains(provider.getId())) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean isUnknown() {
    return myRunConfiguration instanceof UnknownRunConfiguration;
  }

  void doAddAction(AnActionButton button) {
      if (isUnknown()) {
        return;
      }

      final JBPopupFactory popupFactory = JBPopupFactory.getInstance();
      final BeforeRunTaskProvider<BeforeRunTask>[] providers = Extensions.getExtensions(BeforeRunTaskProvider.EXTENSION_POINT_NAME,
                                                                                        myRunConfiguration.getProject());
    Set<Key> activeProviderKeys = getActiveProviderKeys();

    DefaultActionGroup actionGroup = new DefaultActionGroup(null, false);
      for (final BeforeRunTaskProvider<BeforeRunTask> provider : providers) {
        if (provider.createTask(myRunConfiguration) == null)
          continue;
        if (activeProviderKeys.contains(provider.getId()) && provider.isSingleton())
          continue;
        AnAction providerAction = new AnAction(provider.getName(), null, provider.getIcon()) {
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

            Set<RunConfiguration> configurationSet = new HashSet<>();
            getAllRunBeforeRuns(task, configurationSet);
            if (configurationSet.contains(myRunConfiguration)) {
              JOptionPane.showMessageDialog(BeforeRunStepsPanel.this,
                                            ExecutionBundle.message("before.launch.panel.cyclic_dependency_warning",
                                                                    myRunConfiguration.getName(),
                                                                    provider.getDescription(task)),
                                            ExecutionBundle.message("warning.common.title"),JOptionPane.WARNING_MESSAGE);
              return;
            }
            addTask(task);
            myListener.fireStepsBeforeRunChanged();
          }
        };
        actionGroup.add(providerAction);
      }
      final ListPopup popup =
        popupFactory.createActionGroupPopup(ExecutionBundle.message("add.new.run.configuration.action2.name"), actionGroup,
                                            SimpleDataContext.getProjectContext(myRunConfiguration.getProject()), false, false, false, null,
                                            -1, Conditions.alwaysTrue());
      popup.show(button.getPreferredPopupPoint());
  }

  public void addTask(BeforeRunTask task) {
    myModel.add(task);
  }

  private Set<Key> getActiveProviderKeys() {
    Set<Key> result = new HashSet<>();
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

      List<BeforeRunTask<?>> tasks = RunManagerImpl.getInstanceImpl(configuration.getProject()).getBeforeRunTasks(configuration);
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
    void titleChanged(String title);
  }

  private class MyListCellRenderer extends JBList.StripedListCellRenderer {
    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      if (value instanceof BeforeRunTask) {
        BeforeRunTask task = (BeforeRunTask)value;
        BeforeRunTaskProvider<BeforeRunTask> provider = BeforeRunTaskProvider.getProvider(myRunConfiguration.getProject(), task.getProviderId());
        if (provider != null) {
          Icon icon = provider.getTaskIcon(task);
          setIcon(icon != null ? icon : provider.getIcon());
          setText(provider.getDescription(task));
        }
      }
      return this;
    }
  }
}
