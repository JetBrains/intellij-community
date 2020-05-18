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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.WrapLayout;
import org.jetbrains.annotations.NotNull;

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

    public BeforeRunComponent() {
      super(new WrapLayout(FlowLayout.LEADING));
      setBorder(JBUI.Borders.emptyLeft(-5));
      add(new JLabel(ExecutionBundle.message("run.configuration.before.run.label")));
      myAddButton = new InplaceButton(ExecutionBundle.message("run.configuration.before.run.add.task"), AllIcons.General.Add, e -> {
        showPopup();
      });
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
              tag.setVisible(true);
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
        myTags = new ArrayList<>();
        RunConfiguration configuration = s.getConfiguration();
        for (BeforeRunTaskProvider<BeforeRunTask<?>> provider : BeforeRunTaskProvider.EP_NAME.getExtensions(configuration.getProject())) {
          if (provider.createTask(configuration) == null) {
            continue;
          }
          TaskButton button = new TaskButton(provider);
          add(button);
          myTags.add(button);
        }
      }
      List<BeforeRunTask<?>> tasks = s.getManager().getBeforeRunTasks(s.getConfiguration());
      for (TaskButton tag : myTags) {
        tag.setVisible(ContainerUtil.exists(tasks, task -> tag.myProvider.getId() == task.getProviderId()));
      }
    }

    public void apply(RunnerAndConfigurationSettingsImpl s) {
      RunConfiguration configuration = s.getConfiguration();
      List<BeforeRunTask<?>> tasks = myTags.stream()
        .filter(button -> button.isVisible())
        .map(button -> button.myProvider.createTask(configuration))
        .collect(Collectors.toList());
      s.getManager().setBeforeRunTasks(configuration, tasks);
    }

    private final class TaskButton extends TagButton {
      @NotNull private final BeforeRunTaskProvider<BeforeRunTask<?>> myProvider;

      private TaskButton(BeforeRunTaskProvider<BeforeRunTask<?>> provider) {
        super(provider.getName(), myChangeListener);
        myProvider = provider;
        setIcon(provider.getIcon());
      }

      private void setTask(@NotNull BeforeRunTask<?> task) {
        setText(myProvider.getDescription(task));
        setIcon(myProvider.getTaskIcon(task));
      }
    }
  }
}
