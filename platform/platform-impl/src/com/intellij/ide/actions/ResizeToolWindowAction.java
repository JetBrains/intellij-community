// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.execution.impl.ConsoleViewUtil;
import com.intellij.internal.statistic.eventLog.events.EventPair;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.FusAwareAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

public abstract class ResizeToolWindowAction extends AnAction implements DumbAware, FusAwareAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public final void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    Editor editor = e.getData(CommonDataKeys.HOST_EDITOR);
    if (editor == null) editor = e.getData(CommonDataKeys.EDITOR);
    boolean isActiveEditorPresented = editor != null && !ConsoleViewUtil.isConsoleViewEditor(editor) && !editor.isViewer();
    if (project == null || isActiveEditorPresented) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    ToolWindow window = getToolWindow(e);
    Window windowAncestor = window == null ? null : UIUtil.getWindow(window.getComponent());
    if (windowAncestor instanceof JWindow) {
      windowAncestor = windowAncestor.getOwner(); //SearchEverywhere popup case
    }
    if (windowAncestor instanceof IdeFrame && !(windowAncestor instanceof IdeFrame.Child) &&
        window.isAvailable() && window.isVisible() &&
        window.getType() != ToolWindowType.FLOATING &&
        window.getType() != ToolWindowType.WINDOWED) {
      update(e, window);
    }
    else {
      e.getPresentation().setEnabledAndVisible(false);
    }
  }

  private static @Nullable ToolWindow getToolWindow(@NotNull AnActionEvent e) {
    ToolWindow toolWindow = e.getData(PlatformDataKeys.TOOL_WINDOW);
    return toolWindow != null ? toolWindow : ArrayUtil.getFirstElement(e.getData(PlatformDataKeys.LAST_ACTIVE_TOOL_WINDOWS));
  }


  protected abstract void update(@NotNull AnActionEvent event, @NotNull ToolWindow window);

  @Override
  public final void actionPerformed(@NotNull AnActionEvent e) {
    ToolWindow window = getToolWindow(e);
    if (window == null) return;
    actionPerformed(e, window);
  }

  @Override
  public @NotNull List<EventPair<?>> getAdditionalUsageData(@NotNull AnActionEvent event) {
    Project project = event.getProject();
    ToolWindow toolWindow = project == null ? null : getToolWindow(event);
    return toolWindow != null
           ? Collections.singletonList(ToolwindowFusEventFields.TOOLWINDOW.with(toolWindow.getId()))
           : Collections.emptyList();
  }

  protected abstract void actionPerformed(@NotNull AnActionEvent e, @NotNull ToolWindow toolWindow);

  private static void stretch(@NotNull ToolWindow window, boolean isHorizontalStretching, boolean isIncrementAction) {
    ToolWindowAnchor anchor = window.getAnchor();
    Dimension size = WindowAction.getPreferredDelta();
    if (isHorizontalStretching && !anchor.isHorizontal()) {
      int scroll = size.width * Registry.intValue("ide.windowSystem.hScrollChars");
      boolean positive = (anchor == ToolWindowAnchor.LEFT) == isIncrementAction;
      ((ToolWindowEx)window).stretchWidth(positive ? scroll : -scroll);
    }
    else if (!isHorizontalStretching && anchor.isHorizontal()) {
      int scroll = size.height * Registry.intValue("ide.windowSystem.vScrollChars");
      boolean positive = (anchor == ToolWindowAnchor.TOP) != isIncrementAction;
      ((ToolWindowEx)window).stretchHeight(positive ? scroll : -scroll);
    }
  }

  public static class Left extends ResizeToolWindowAction {
    @Override
    protected void update(@NotNull AnActionEvent event, @NotNull ToolWindow window) {
      event.getPresentation().setEnabled(!window.getAnchor().isHorizontal());
    }

    @Override
    protected void actionPerformed(@NotNull AnActionEvent e, @NotNull ToolWindow toolWindow) {
      stretch(toolWindow, true, false);
    }
  }

  public static class Right extends ResizeToolWindowAction {
    @Override
    protected void update(@NotNull AnActionEvent event, @NotNull ToolWindow window) {
      event.getPresentation().setEnabled(!window.getAnchor().isHorizontal());
    }

    @Override
    protected void actionPerformed(@NotNull AnActionEvent e, @NotNull ToolWindow toolWindow) {
      stretch(toolWindow, true, true);
    }
  }

  public static class Up extends ResizeToolWindowAction {
    @Override
    protected void update(@NotNull AnActionEvent event, @NotNull ToolWindow window) {
      event.getPresentation().setEnabled(window.getAnchor().isHorizontal());
    }

    @Override
    protected void actionPerformed(@NotNull AnActionEvent e, @NotNull ToolWindow toolWindow) {
      stretch(toolWindow, false, true);
    }
  }

  public static class Down extends ResizeToolWindowAction {
    @Override
    protected void update(@NotNull AnActionEvent event, @NotNull ToolWindow window) {
      event.getPresentation().setEnabled(window.getAnchor().isHorizontal());
    }

    @Override
    protected void actionPerformed(@NotNull AnActionEvent e, @NotNull ToolWindow toolWindow) {
      stretch(toolWindow, false, false);
    }
  }
}
