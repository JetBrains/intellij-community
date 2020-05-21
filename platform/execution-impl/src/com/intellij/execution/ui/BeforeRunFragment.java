// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Conditions;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.WrapLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class BeforeRunFragment<S extends RunConfigurationBase<?>> extends RunConfigurationEditorFragment<S, BeforeRunFragment.BeforeRunComponent> {
  public static <S extends RunConfigurationBase<?>> List<SettingsEditorFragment<S, ?>> createGroup() {
    ArrayList<SettingsEditorFragment<S, ?>> list = new ArrayList<>();
    list.add(new BeforeRunFragment<>());
    list.add(RunConfigurationEditorFragment.createSettingsTag("before.launch.openToolWindow",
                                                              ExecutionBundle.message("run.configuration.before.run.open.tool.window"),
                                                              ExecutionBundle.message("run.configuration.before.run.group"),
                                                              settings -> settings.isActivateToolWindowBeforeRun(),
                                                              (settings, value) -> settings.setActivateToolWindowBeforeRun(value)));
    list.add(RunConfigurationEditorFragment.createSettingsTag("before.launch.editSettings",
                                                              ExecutionBundle.message("run.configuration.before.run.edit.settings"),
                                                              ExecutionBundle.message("run.configuration.before.run.group"),
                                                              settings -> settings.isEditBeforeRun(),
                                                              (settings, value) -> settings.setEditBeforeRun(value)));
    return list;
  }

  private BeforeRunFragment() {
    super("beforeRunTasks", ExecutionBundle.message("run.configuration.before.run.task"),
          ExecutionBundle.message("run.configuration.before.run.group"), new BeforeRunComponent(), -2);
    component().myChangeListener = () -> fireEditorStateChanged();
  }

  @Override
  public void toggle(boolean selected) {
    super.setSelected(selected);
    if (selected) {
      component().showPopup();
    }
  }

  @Override
  public void resetEditorFrom(@NotNull RunnerAndConfigurationSettingsImpl s) {
    component().reset(s);
  }

  @Override
  public void applyEditorTo(@NotNull RunnerAndConfigurationSettingsImpl s) {
    component().apply(s);
  }

  public static class BeforeRunComponent extends JPanel {
    private List<TaskButton> myTags;
    private final InplaceButton myAddButton;
    private Runnable myChangeListener;
    private RunConfiguration myConfiguration;
    private final LinkLabel<Object> myAddLabel;

    public BeforeRunComponent() {
      super(new WrapLayout(FlowLayout.LEADING));
      setBorder(JBUI.Borders.emptyLeft(-5));
      add(new JLabel(ExecutionBundle.message("run.configuration.before.run.label")));
      myAddButton = new InplaceButton(ExecutionBundle.message("run.configuration.before.run.add.task"), AllIcons.General.Add, e -> showPopup());
      myAddLabel =
        new LinkLabel<>(ExecutionBundle.message("run.configuration.before.run.add.task"), null, (aSource, aLinkData) -> showPopup());
      add(myAddButton);
    }

    public void showPopup() {
      DefaultActionGroup group = new DefaultActionGroup();
      for (TaskButton tag : myTags) {
        if (tag.isVisible()) {
          continue;
        }
        group.add(new AnAction(tag.myProvider.getName(), null, tag.myProvider.getIcon()) {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            BeforeRunTask<?> task = tag.myProvider.createTask(myConfiguration);
            if (task == null) return;
            tag.myProvider.configureTask(e.getDataContext(), myConfiguration, task).onSuccess(changed -> {
              if (!tag.myProvider.canExecuteTask(myConfiguration, task)) {
                return;
              }
              task.setEnabled(true);
              tag.setTask(task);
              myChangeListener.run();
            });
          }
        });
      }
      ListPopup
        popup = JBPopupFactory
        .getInstance().createActionGroupPopup(ExecutionBundle.message("add.new.run.configuration.action2.name"), group,
                                              DataManager.getInstance().getDataContext(myAddButton), false, false, false, null,
                                              -1, Conditions.alwaysTrue());
      popup.showUnderneathOf(myAddButton);
    }

    public void reset(RunnerAndConfigurationSettingsImpl s) {
      myConfiguration = s.getConfiguration();
      if (myTags == null) {
        addButtons();
      }
      List<BeforeRunTask<?>> tasks = s.getManager().getBeforeRunTasks(s.getConfiguration());
      for (BeforeRunTask<?> task : tasks) {
        TaskButton button = ContainerUtil.find(myTags, tag -> tag.myProvider.getId() == task.getProviderId());
        if (button != null) {
          button.setTask(task);
        }
      }
      updateAddLabel();
    }

    private void updateAddLabel() {
      myAddLabel.setVisible(getEnabledTasks().isEmpty());
    }

    private void addButtons() {
      myTags = new ArrayList<>();
      for (BeforeRunTaskProvider<BeforeRunTask<?>> provider : BeforeRunTaskProvider.EP_NAME.getExtensions(myConfiguration.getProject())) {
        if (provider.createTask(myConfiguration) == null) {
          continue;
        }
        TaskButton button = new TaskButton(provider, () -> {
          myChangeListener.run();
          updateAddLabel();
        });
        add(button);
        myTags.add(button);
      }
      add(myAddButton);
      add(myAddLabel);
    }

    public void apply(RunnerAndConfigurationSettingsImpl s) {
      s.getManager().setBeforeRunTasks(s.getConfiguration(), getEnabledTasks());
    }

    @NotNull
    private List<BeforeRunTask<?>> getEnabledTasks() {
      return myTags.stream()
        .filter(button -> button.myTask != null && button.isVisible())
        .map(button -> button.myTask)
        .collect(Collectors.toList());
    }

    private static final class TaskButton extends TagButton {
      @NotNull private final BeforeRunTaskProvider<BeforeRunTask<?>> myProvider;
      private BeforeRunTask<?> myTask;

      private TaskButton(BeforeRunTaskProvider<BeforeRunTask<?>> provider, Runnable action) {
        super(provider.getName(), action);
        myProvider = provider;
        setVisible(false);
      }

      private void setTask(@Nullable BeforeRunTask<?> task) {
        myTask = task;
        setVisible(task != null);
        if (task != null) {
          setText(myProvider.getDescription(task));
          setIcon(myProvider.getTaskIcon(task));
        }
      }
    }
  }
}
