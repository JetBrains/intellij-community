// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.dnd.*;
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

public class BeforeRunComponent extends JPanel implements DnDTarget {
  private List<TaskButton> myTags;
  private final InplaceButton myAddButton;
  Runnable myChangeListener;
  private RunConfiguration myConfiguration;
  private final LinkLabel<Object> myAddLabel;

  public BeforeRunComponent() {
    super(new WrapLayout(FlowLayout.LEADING));
    setBorder(JBUI.Borders.emptyLeft(-5));
    add(new JLabel(ExecutionBundle.message("run.configuration.before.run.label")));
    myAddButton = new InplaceButton(ExecutionBundle.message("run.configuration.before.run.add.task"), AllIcons.General.Add, e -> showPopup());
    myAddLabel =
      new LinkLabel<>(ExecutionBundle.message("run.configuration.before.run.add.task"), null, (aSource, aLinkData) -> showPopup());
    DnDManager.getInstance().registerTarget(this, this);
  }

  private List<BeforeRunTaskProvider<BeforeRunTask<?>>> getProviders() {
    return ContainerUtil.filter(BeforeRunTaskProvider.EP_NAME.getExtensions(myConfiguration.getProject()),
                                provider -> provider.createTask(myConfiguration) != null);
  }

  private void createTags() {
    myTags = new ArrayList<>();
    for (BeforeRunTaskProvider<BeforeRunTask<?>> provider : getProviders()) {
      TaskButton button = new TaskButton(provider, () -> {
        myChangeListener.run();
        updateAddLabel();
      });
      myTags.add(button);
    }
  }

  private void updateAddLabel() {
    myAddLabel.setVisible(getEnabledTasks().isEmpty());
  }

  public void showPopup() {
    DefaultActionGroup group = new DefaultActionGroup();
    for (BeforeRunTaskProvider<BeforeRunTask<?>> provider : getProviders()) {
      TaskButton tag = ContainerUtil.find(myTags, t -> t.myProvider.getId() == provider.getId());
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
            myTags.remove(tag);
            myTags.add(tag);
            buildPanel();
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
      createTags();
    }
    List<BeforeRunTask<?>> tasks = s.getManager().getBeforeRunTasks(s.getConfiguration());
    for (BeforeRunTask<?> task : tasks) {
      TaskButton button = ContainerUtil.find(myTags, tag -> tag.myProvider.getId() == task.getProviderId());
      if (button != null) {
        button.setTask(task);
        myTags.remove(button);
        myTags.add(button);
      }
    }
    buildPanel();
  }

  private void buildPanel() {
    remove(myAddButton);
    remove(myAddLabel);
    for (TaskButton tag : myTags) {
      remove(tag);
    }
    for (TaskButton tag : myTags) {
      if (tag.isVisible()) {
        add(tag);
      }
    }
    add(myAddButton);
    add(myAddLabel);
    updateAddLabel();
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

  @Override
  public void drop(DnDEvent event) {
    Object object = event.getAttachedObject();
    if (!(object instanceof TaskButton)) {
      return;
    }
    Rectangle area = new Rectangle(event.getPoint().x - 5, event.getPoint().y - 5, 10, 10);
    int i = ContainerUtil.indexOf(myTags, button -> button.isVisible() && button.getBounds().intersects(area));
    if (i < 0 || myTags.get(i) == object) return;
    myTags.remove(object);
    myTags.add(i, (TaskButton)object);
    buildPanel();
    myChangeListener.run();
  }

  @Override
  public boolean update(DnDEvent event) {
    if (event.getAttachedObject() instanceof TaskButton) {
      event.setDropPossible(true);
      return false;
    }
    return true;
  }

  private static final class TaskButton extends TagButton implements DnDSource {
    @NotNull private final BeforeRunTaskProvider<BeforeRunTask<?>> myProvider;
    private BeforeRunTask<?> myTask;

    private TaskButton(BeforeRunTaskProvider<BeforeRunTask<?>> provider, Runnable action) {
      super(provider.getName(), action);
      myProvider = provider;
      setVisible(false);
      DnDManager.getInstance().registerSource(this, this);
    }

    private void setTask(@Nullable BeforeRunTask<?> task) {
      myTask = task;
      setVisible(task != null);
      if (task != null) {
        setText(myProvider.getDescription(task));
        setIcon(myProvider.getTaskIcon(task));
      }
    }

    @Override
    public void dispose() {
      super.dispose();
      DnDManager.getInstance().unregisterSource(this, this);
    }

    @Override
    public boolean canStartDragging(DnDAction action, Point dragOrigin) {
      return true;
    }

    @Override
    public DnDDragStartBean startDragging(DnDAction action, Point dragOrigin) {
      return new DnDDragStartBean(this);
    }

    @Override
    public String toString() {
      return myProvider.getName();
    }
  }
}
