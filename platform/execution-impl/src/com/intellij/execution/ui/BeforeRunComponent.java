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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.WrapLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public final class BeforeRunComponent extends JPanel implements DnDTarget {
  private List<TaskButton> myTags;
  private final InplaceButton myAddButton;
  private final JPanel myAddPanel;
  private final LinkLabel<Object> myAddLabel;
  private final JLabel myDropFirst = new JLabel(AllIcons.General.DropPlace);

  Runnable myChangeListener;
  private BiConsumer<Key<? extends BeforeRunTask<?>>, Boolean> myTagListener;
  private RunConfiguration myConfiguration;

  public BeforeRunComponent(@NotNull Disposable parentDisposable) {
    super(new WrapLayout(FlowLayout.LEADING, 0, 0));
    add(Box.createVerticalStrut(35));

    JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
    myDropFirst.setBorder(JBUI.Borders.emptyTop(5));
    panel.add(myDropFirst);
    panel.setPreferredSize(myDropFirst.getPreferredSize());
    add(panel);
    myDropFirst.setVisible(false);
    JBEmptyBorder border = JBUI.Borders.empty(5, 0, 0, 5);
    myAddButton = new InplaceButton(ExecutionBundle.message("run.configuration.before.run.add.task"), AllIcons.General.Add, e -> showPopup());
    myAddPanel = new JPanel();
    myAddPanel.setBorder(border);
    myAddPanel.add(myAddButton);
    myAddLabel = new LinkLabel<>(ExecutionBundle.message("run.configuration.before.run.add.task"), null, (aSource, aLinkData) -> showPopup());
    myAddLabel.setBorder(border);
    DnDManager.getInstance().registerTarget(this, this, parentDisposable);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        if (myTags != null) {
          myTags.forEach(Disposer::dispose);
          myTags = null;
        }
      }
    });
  }

  private List<BeforeRunTaskProvider<BeforeRunTask<?>>> getProviders() {
    return ContainerUtil.filter(BeforeRunTaskProvider.EP_NAME.getExtensions(myConfiguration.getProject()),
                                provider -> provider.createTask(myConfiguration) != null);
  }

  private void createTags() {
    myTags = new ArrayList<>();
    for (BeforeRunTaskProvider<BeforeRunTask<?>> provider : getProviders()) {
      myTags.add(new TaskButton(provider, () -> {
        myChangeListener.run();
        updateAddLabel();
        myTagListener.accept(provider.getId(), false);
      }));
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
          createTask(e, tag);
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

  public void addOrRemove(Key<? extends BeforeRunTask<?>> providerId, boolean add) {
    if (myTags == null) return;
    TaskButton taskButton = ContainerUtil.find(myTags, button -> button.myProvider.getId() == providerId);
    if (add) {
      if (!taskButton.isVisible()) {
        createTask(null, taskButton);
      }
    }
    else {
      taskButton.setVisible(false);
    }
  }

  private void createTask(@Nullable AnActionEvent e, TaskButton tag) {
    BeforeRunTask<?> task = tag.myProvider.createTask(myConfiguration);
    if (task == null) return;
    if (e == null) {
      addTask(tag, task);
      return;
    }
    tag.myProvider.configureTask(e.getDataContext(), myConfiguration, task).onSuccess(changed -> {
      if (!tag.myProvider.canExecuteTask(myConfiguration, task)) {
        return;
      }
      addTask(tag, task);
    });
  }

  private void addTask(TaskButton tag, BeforeRunTask<?> task) {
    task.setEnabled(true);
    tag.setTask(task);
    myTags.remove(tag);
    myTags.add(tag);
    buildPanel();
    myChangeListener.run();
    myTagListener.accept(tag.myProvider.getId(), true);
  }

  public void reset(@NotNull RunnerAndConfigurationSettingsImpl s) {
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
    remove(myAddPanel);
    remove(myAddLabel);
    for (TaskButton tag : myTags) {
      remove(tag);
    }
    for (TaskButton tag : myTags) {
      if (tag.isVisible()) {
        add(tag);
      }
    }
    add(myAddPanel);
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
    TagButton replaceButton = getReplaceButton(event);
    if (replaceButton == null ) return;
    TaskButton button = (TaskButton)event.getAttachedObject();
    int i = myTags.indexOf(replaceButton);
    myTags.remove(button);
    myTags.add(i, button);
    buildPanel();
    myChangeListener.run();
    IdeFocusManager.getInstance(myConfiguration.getProject()).requestFocus(button, false);
  }

  @Override
  public void cleanUpOnLeave() {
    if (myTags != null) {
      myTags.forEach(button -> button.showDropPlace(false));
    }
    myDropFirst.setVisible(false);
  }

  private TagButton getReplaceButton(DnDEvent event) {
    Object object = event.getAttachedObject();
    if (!(object instanceof TaskButton)) {
      return null;
    }
    Rectangle area = new Rectangle(event.getPoint().x - 5, event.getPoint().y - 5, 10, 10);
    TaskButton button = ContainerUtil.find(myTags, tag -> tag.isVisible() && tag.getBounds().intersects(area));
    if (button == null || button == object) return null;
    boolean left = button.getBounds().getCenterX() > event.getPoint().x;
    int i = myTags.indexOf(button);
    if (i < myTags.indexOf(object)) {
      if (!left) {
        button = ContainerUtil.find(myTags, b -> b.isVisible() && myTags.indexOf(b) > i);
      }
    }
    else if (left) {
      button = ContainerUtil.findLast(myTags, b -> b.isVisible() && myTags.indexOf(b) < i);
    }
    return button == object ? null : button;
  }

  private TagButton getDropButton(TagButton replaceButton, DnDEvent event) {
    int i = myTags.indexOf(replaceButton);
    if (i > myTags.indexOf(event.getAttachedObject())) {
      return replaceButton;
    }
    return ContainerUtil.findLast(myTags, button -> button.isVisible() && myTags.indexOf(button) < i);
  }

  @Override
  public boolean update(DnDEvent event) {
    TagButton replace = getReplaceButton(event);
    if (replace != null) {
      TagButton dropButton = getDropButton(replace, event);
      myTags.forEach(button -> button.showDropPlace(button == dropButton));
      myDropFirst.setVisible(dropButton == null);
      event.setDropPossible(true);
      return false;
    }
    myTags.forEach(button -> button.showDropPlace(false));
    event.setDropPossible(false);
    return true;
  }

  public void setTagListener(BiConsumer<Key<? extends BeforeRunTask<?>>, Boolean> tagListener) {
    myTagListener = tagListener;
  }

  private final class TaskButton extends TagButton implements DnDSource {
    private final BeforeRunTaskProvider<BeforeRunTask<?>> myProvider;
    private final JLabel myDropPlace = new JLabel(AllIcons.General.DropPlace);
    private BeforeRunTask<?> myTask;

    private TaskButton(@NotNull BeforeRunTaskProvider<BeforeRunTask<?>> provider, @NotNull Runnable action) {
      super(provider.getName(), action);
      add(myDropPlace, JLayeredPane.DRAG_LAYER);
      myProvider = provider;
      setVisible(false);
      myDropPlace.setVisible(false);
      myButton.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          if (e.getClickCount() == 2) {
            myProvider.configureTask(DataManager.getInstance().getDataContext(TaskButton.this), myConfiguration, myTask)
              .onSuccess(aBoolean -> setTask(myTask));
          }
        }
      });
      DnDManager.getInstance().registerSource(this, myButton, this);
      myButton.setToolTipText(ExecutionBundle.message("run.configuration.before.run.tooltip"));
      layoutButtons();
    }

    @Override
    protected void layoutButtons() {
      super.layoutButtons();
      if (myDropPlace == null) return;
      Rectangle bounds = myButton.getBounds();
      Dimension size = myDropPlace.getPreferredSize();
      myDropPlace.setBounds(bounds.x + bounds.width + 2, bounds.y + (bounds.height - size.height)/2, size.width, size.height);
    }

    private void setTask(@Nullable BeforeRunTask<?> task) {
      myTask = task;
      setVisible(task != null);
      if (task != null) {
        updateButton(myProvider.getDescription(task), myProvider.getTaskIcon(task));
      }
    }

    private void showDropPlace(boolean show) {
      myDropPlace.setVisible(show);
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
