// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl;

import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.UnknownRunConfiguration;
import com.intellij.execution.impl.RunConfigurationBeforeRunProvider.RunConfigurableBeforeRunTask;
import com.intellij.execution.impl.statistics.RunConfigurationOptionUsagesCollector;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.SmartList;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.*;

/**
 * @author Vassiliy Kudryashov
 */
@ApiStatus.Internal
public final class BeforeRunStepsPanel extends JPanel {
  private final JCheckBox myShowSettingsBeforeRunCheckBox;
  private final JCheckBox myActivateToolWindowBeforeRunCheckBox;
  private final JCheckBox myFocusToolWindowBeforeRunCheckBox;
  private final JBList<BeforeRunTask<?>> myList;
  private final CollectionListModel<BeforeRunTask<?>> myModel;
  private RunConfiguration myRunConfiguration;

  private final List<BeforeRunTask<?>> originalTasks = new SmartList<>();
  private final StepsBeforeRunListener myListener;
  private final JPanel myPanel;

  private final Set<BeforeRunTask<?>> clonedTasks = CollectionFactory.createSmallMemoryFootprintSet();

  public BeforeRunStepsPanel(@NotNull StepsBeforeRunListener listener) {
    myListener = listener;
    myModel = new CollectionListModel<>();
    myList = new JBList<>(myModel);
    myList.getEmptyText().setText(ExecutionBundle.message("before.launch.panel.empty"));
    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myList.setCellRenderer(new MyListCellRenderer());
    myList.setVisibleRowCount(4);

    myModel.addListDataListener(new ListDataListener() {
      @Override
      public void intervalAdded(ListDataEvent e) {
        updateText();
      }

      @Override
      public void intervalRemoved(ListDataEvent e) {
        updateText();
      }

      @Override
      public void contentsChanged(ListDataEvent e) {
      }
    });

    ToolbarDecorator myDecorator = ToolbarDecorator.createDecorator(myList);

    myDecorator.setEditAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        BeforeRunTaskAndProvider selection = getSelection();
        if (selection == null) {
          return;
        }

        BeforeRunTask<?> task = selection.getTask();
        if (!clonedTasks.contains(task)) {
          task = task.clone();
          clonedTasks.add(task);
          myModel.setElementAt(task, selection.getIndex());
        }
        RunConfigurationOptionUsagesCollector.logEditBeforeRunTask(
          myRunConfiguration.getProject(), myRunConfiguration.getType().getId(), task.getProviderId().getClass());
        selection.getProvider().configureTask(button.getDataContext(), myRunConfiguration, task)
          .onSuccess(changed -> {
            if (changed) {
              updateText();
            }
          });
      }
    });
    //noinspection Convert2Lambda
    myDecorator.setEditActionUpdater(new AnActionButtonUpdater() {
      @Override
      public boolean isEnabled(@NotNull AnActionEvent e) {
        BeforeRunTaskAndProvider selection = getSelection();
        return selection != null && selection.getProvider().isConfigurable();
      }
    });
    myDecorator.setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        doAddAction(button);
      }
    });
    //noinspection Convert2Lambda
    myDecorator.setAddActionUpdater(new AnActionButtonUpdater() {
      @Override
      public boolean isEnabled(@NotNull AnActionEvent e) {
        return checkBeforeRunTasksAbility(true);
      }
    });

    myDecorator.setRemoveAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        BeforeRunTaskAndProvider selection = getSelection();
        if (selection != null) {
          RunConfigurationOptionUsagesCollector.logRemoveBeforeRunTask(
            myRunConfiguration.getProject(), myRunConfiguration.getType().getId(), selection.getProvider().getClass());
          ListUtil.removeSelectedItems(myList);
        }
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
    myFocusToolWindowBeforeRunCheckBox = new JCheckBox(ExecutionBundle.message("configuration.focus.toolwindow.before.run"));

    myPanel = myDecorator.createPanel();
    myDecorator.getActionsPanel().setCustomShortcuts(CommonActionsPanel.Buttons.EDIT,
                                                     CommonActionsPanel.getCommonShortcut(CommonActionsPanel.Buttons.EDIT),
                                                     CommonShortcuts.DOUBLE_CLICK_1);


    setLayout(new BorderLayout());
    add(myPanel, BorderLayout.CENTER);
    JPanel checkboxPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, JBUIScale.scale(10), JBUIScale.scale(5)));
    checkboxPanel.add(myShowSettingsBeforeRunCheckBox);
    checkboxPanel.add(myActivateToolWindowBeforeRunCheckBox);
    checkboxPanel.add(myFocusToolWindowBeforeRunCheckBox);
    add(checkboxPanel, BorderLayout.SOUTH);
  }

  @Override
  public void setVisible(boolean aFlag) {
    super.setVisible(aFlag);
    updateText();
  }

  private @Nullable BeforeRunTaskAndProvider getSelection() {
    int index = myList.getSelectedIndex();
    if (index == -1) {
      return null;
    }
    BeforeRunTask<?> task = myModel.getElementAt(index);
    BeforeRunTaskProvider<BeforeRunTask<?>> provider = getProvider(myRunConfiguration.getProject(), task.getProviderId());
    return provider == null ? null : new BeforeRunTaskAndProvider(task, provider, index);
  }

  public void doReset(@NotNull RunnerAndConfigurationSettings settings) {
    clonedTasks.clear();

    myRunConfiguration = settings.getConfiguration();

    originalTasks.clear();
    originalTasks.addAll(RunManagerImplKt.doGetBeforeRunTasks(myRunConfiguration));
    myModel.replaceAll(originalTasks);
    myShowSettingsBeforeRunCheckBox.setSelected(settings.isEditBeforeRun());
    myShowSettingsBeforeRunCheckBox.setEnabled(!isUnknown());
    myActivateToolWindowBeforeRunCheckBox.setSelected(settings.isActivateToolWindowBeforeRun());
    myActivateToolWindowBeforeRunCheckBox.setEnabled(!isUnknown());
    myFocusToolWindowBeforeRunCheckBox.setSelected(settings.isFocusToolWindowBeforeRun());
    myFocusToolWindowBeforeRunCheckBox.setEnabled(!isUnknown());
    myPanel.setVisible(checkBeforeRunTasksAbility(false));
    updateText();
  }

  private void updateText() {
    int count = myModel.getSize();
    String title = ExecutionBundle.message("before.launch.panel.title");
    String suffix = count == 0 || isVisible() ? "" : ExecutionBundle.message("before.launch.panel.title.suffix", count);
    myListener.titleChanged(title + suffix);
  }

  public @NotNull List<BeforeRunTask<?>> getTasks() {
    List<BeforeRunTask<?>> items = myModel.getItems();
    return items.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(items);
  }

  public boolean needEditBeforeRun() {
    return myShowSettingsBeforeRunCheckBox.isSelected();
  }

  public boolean needActivateToolWindowBeforeRun() {
    return myActivateToolWindowBeforeRunCheckBox.isSelected();
  }

  public boolean needFocusToolWindowBeforeRun() {
    return myFocusToolWindowBeforeRunCheckBox.isSelected();
  }

  private boolean checkBeforeRunTasksAbility(boolean checkOnlyAddAction) {
    if (isUnknown()) {
      return false;
    }

    Set<Key<?>> activeProviderKeys = getActiveProviderKeys();
    for (BeforeRunTaskProvider<BeforeRunTask<?>> provider : getBeforeRunTaskProviders()) {
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

  private void doAddAction(@NotNull AnActionButton button) {
    if (isUnknown()) {
      return;
    }

    Set<Key<?>> activeProviderKeys = getActiveProviderKeys();
    DefaultActionGroup actionGroup = new DefaultActionGroup();
    for (BeforeRunTaskProvider<BeforeRunTask<?>> provider : getBeforeRunTaskProviders()) {
      if (provider.createTask(myRunConfiguration) == null || activeProviderKeys.contains(provider.getId()) && provider.isSingleton()) {
        continue;
      }

      actionGroup.add(new AnAction(provider.getName(), null, provider.getIcon()) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          BeforeRunTask<?> task = provider.createTask(myRunConfiguration);
          if (task == null) {
            return;
          }

          provider.configureTask(button.getDataContext(), myRunConfiguration, task)
            .onSuccess(changed -> {
              if (!provider.canExecuteTask(myRunConfiguration, task)) {
                return;
              }
              task.setEnabled(true);

              Set<RunConfiguration> configurationSet = CollectionFactory.createSmallMemoryFootprintSet();
              getAllRunBeforeRuns(task, configurationSet);
              if (configurationSet.contains(myRunConfiguration)) {
                JOptionPane.showMessageDialog(BeforeRunStepsPanel.this,
                                              ExecutionBundle.message("before.launch.panel.cyclic_dependency_warning",
                                                                      myRunConfiguration.getName(),
                                                                      provider.getDescription(task)),
                                              ExecutionBundle.message("warning.common.title"), JOptionPane.WARNING_MESSAGE);
                return;
              }
              RunConfigurationOptionUsagesCollector.logAddBeforeRunTask(
                myRunConfiguration.getProject(), myRunConfiguration.getType().getId(), provider.getClass());
              addTask(task);
              myListener.fireStepsBeforeRunChanged();
            });
        }
      });
    }
    DataContext dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, myRunConfiguration.getProject())
      .add(PlatformCoreDataKeys.CONTEXT_COMPONENT, myPanel)
      .build();
    ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(ExecutionBundle.message("add.new.before.run.task.name"), actionGroup,
                                                                          dataContext, false, false, false, null,
                                                                          -1, Conditions.alwaysTrue());
    popup.show(Objects.requireNonNull(button.getPreferredPopupPoint()));
  }

  private @NotNull List<BeforeRunTaskProvider<BeforeRunTask<?>>> getBeforeRunTaskProviders() {
    return BeforeRunTaskProvider.EP_NAME.getExtensions(myRunConfiguration.getProject());
  }

  public void addTask(@NotNull BeforeRunTask<?> task) {
    myModel.add(task);
  }

  public void replaceTasks(@NotNull List<BeforeRunTask<?>> tasks) {
    myModel.replaceAll(tasks);
  }

  private @NotNull Set<Key<?>> getActiveProviderKeys() {
    List<BeforeRunTask<?>> items = myModel.getItems();
    Set<Key<?>> result = CollectionFactory.createSmallMemoryFootprintSet(items.size());
    for (BeforeRunTask<?> task : items) {
      result.add(task.getProviderId());
    }
    return result;
  }

  private void getAllRunBeforeRuns(@NotNull BeforeRunTask<?> task, @NotNull Set<? super RunConfiguration> configurationSet) {
    if (task instanceof RunConfigurableBeforeRunTask) {
      RunConfiguration configuration = Objects.requireNonNull(((RunConfigurableBeforeRunTask)task).getSettings()).getConfiguration();
      for (BeforeRunTask<?> beforeRunTask : RunManagerImplKt.doGetBeforeRunTasks(configuration)) {
        if (beforeRunTask instanceof RunConfigurableBeforeRunTask) {
          if (configurationSet.add(Objects.requireNonNull(((RunConfigurableBeforeRunTask)beforeRunTask).getSettings()).getConfiguration())) {
            getAllRunBeforeRuns(beforeRunTask, configurationSet);
          }
        }
      }
    }
  }

  public interface StepsBeforeRunListener {
    void fireStepsBeforeRunChanged();

    void titleChanged(@NotNull @NlsContexts.Separator String title);
  }

  private final class MyListCellRenderer extends JBList.StripedListCellRenderer {
    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      if (value instanceof BeforeRunTask<?> task) {
        //noinspection rawtypes
        BeforeRunTaskProvider provider = getProvider(myRunConfiguration.getProject(), task.getProviderId());
        if (provider == null) {
          provider = new UnknownBeforeRunTaskProvider(task.getProviderId().toString());
        }
        //noinspection unchecked
        Icon icon = provider.getTaskIcon(task);
        setIcon(icon != null ? icon : provider.getIcon());
        //noinspection unchecked
        setText(provider.getDescription(task));
      }
      return this;
    }
  }

  private static @Nullable BeforeRunTaskProvider<BeforeRunTask<?>> getProvider(@NotNull Project project, Key<?> key) {
    for (Iterator<BeforeRunTaskProvider<BeforeRunTask<?>>> it = BeforeRunTaskProvider.EP_NAME.asSequence(project).iterator();
         it.hasNext(); ) {
      BeforeRunTaskProvider<BeforeRunTask<?>> provider = it.next();
      if (provider.getId() == key) {
        return provider;
      }
    }
    return null;
  }
}