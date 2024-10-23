// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListItemDescriptorAdapter;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.list.GroupedItemsListRenderer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class StopBackgroundProcessesAction extends DumbAwareAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(!getCancellableProcesses(e.getProject()).isEmpty());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    Project project = e.getProject();
    List<StopAction.HandlerItem> handlerItems = getItemsList(getCancellableProcesses(project));

    if (handlerItems.isEmpty()) {
      return;
    }

    final JBList<StopAction.HandlerItem> list = new JBList<>(handlerItems);
    list.setCellRenderer(new GroupedItemsListRenderer<>(new ListItemDescriptorAdapter<>() {
      @Override
      public @Nullable String getTextFor(StopAction.HandlerItem item) {
        return item.displayName;
      }

      @Override
      public @Nullable Icon getIconFor(StopAction.HandlerItem item) {
        return item.icon;
      }

      @Override
      public boolean hasSeparatorAboveOf(StopAction.HandlerItem item) {
        return item.hasSeparator;
      }
    }));

    JBPopup popup = JBPopupFactory.getInstance().createListPopupBuilder(list)
      .setMovable(true)
      .setTitle(handlerItems.size() == 1 ? ExecutionBundle.message("confirm.background.process.stop")
                                         : ExecutionBundle.message("stop.background.process"))
      .setNamerForFiltering(o -> o.displayName)
      .setItemChosenCallback(() -> {
        List valuesList = list.getSelectedValuesList();
        for (Object o : valuesList) {
          if (o instanceof StopAction.HandlerItem) ((StopAction.HandlerItem)o).stop();
        }
      })
      .setRequestFocus(true)
      .createPopup();

    InputEvent inputEvent = e.getInputEvent();
    Component component = inputEvent != null ? inputEvent.getComponent() : null;
    if (component != null && (ActionPlaces.MAIN_TOOLBAR.equals(e.getPlace())
                              || ActionPlaces.NAVIGATION_BAR_TOOLBAR.equals(e.getPlace()))) {
      popup.showUnderneathOf(component);
    }
    else if (project == null) {
      popup.showInBestPositionFor(dataContext);
    }
    else {
      popup.showCenteredInCurrentWindow(project);
    }

  }

  @Unmodifiable
  private static @NotNull List<Pair<TaskInfo, ProgressIndicator>>  getCancellableProcesses(@Nullable Project project) {
    IdeFrame frame = WindowManagerEx.getInstanceEx().findFrameFor(project);
    StatusBarEx statusBar = frame == null ? null : (StatusBarEx)frame.getStatusBar();
    if (statusBar == null) return Collections.emptyList();

    return ContainerUtil.findAll(statusBar.getBackgroundProcesses(),
                                 pair -> pair.first.isCancellable() && !pair.second.isCanceled());
  }

  private static @NotNull List<StopAction.HandlerItem> getItemsList(@NotNull List<? extends Pair<TaskInfo, ProgressIndicator>> tasks) {
    List<StopAction.HandlerItem> items = new ArrayList<>(tasks.size());
    for (final Pair<TaskInfo, ProgressIndicator> eachPair : tasks) {
      items.add(new StopAction.HandlerItem(eachPair.first.getTitle(), AllIcons.Process.Step_passive, false) {
        @Override
        void stop() {
          eachPair.second.cancel();
        }
      });
    }
    return items;
  }
}
