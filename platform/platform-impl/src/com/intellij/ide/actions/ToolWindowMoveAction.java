// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.internal.statistic.eventLog.events.EventPair;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.impl.FusAwareAction;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowInfo;
import com.intellij.openapi.wm.impl.ToolWindowMoveToAction;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.UIBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ToolWindowMoveAction extends DumbAwareAction implements FusAwareAction {
  public enum Anchor {
    LeftTop, LeftBottom, BottomLeft, BottomRight, RightBottom, RightTop, TopRight, TopLeft;

    @Override
    @Nls
    public String toString() {
      String top = UIBundle.message("tool.window.move.to.top.action.name");
      String left = UIBundle.message("tool.window.move.to.left.action.name");
      String bottom = UIBundle.message("tool.window.move.to.bottom.action.name");
      String right = UIBundle.message("tool.window.move.to.right.action.name");
      return switch (this) {
        case LeftTop -> left + " " + top;
        case BottomLeft -> ExperimentalUI.isNewUI() ? left + " " + bottom : bottom + " " + left;
        case BottomRight -> ExperimentalUI.isNewUI() ? right + " " + bottom : bottom + " " + right;
        case RightTop -> right + " " + top;
        case LeftBottom -> ExperimentalUI.isNewUI() ? bottom + " " + left : left + " " + bottom;
        case RightBottom -> ExperimentalUI.isNewUI() ? bottom + " " + right : right + " " + bottom;
        case TopRight -> top + " " + right;
        case TopLeft -> top + " " + left;
      };
    }

    @NotNull
    public static Anchor fromWindowInfo(@NotNull WindowInfo info) {
      if (info.isSplit()) {
        if (info.getAnchor() == ToolWindowAnchor.LEFT) return LeftBottom;
        if (info.getAnchor() == ToolWindowAnchor.BOTTOM) return BottomRight;
        if (info.getAnchor() == ToolWindowAnchor.RIGHT) return RightBottom;
        if (info.getAnchor() == ToolWindowAnchor.TOP) return TopRight;
      }

      if (info.getAnchor() == ToolWindowAnchor.LEFT) return LeftTop;
      if (info.getAnchor() == ToolWindowAnchor.BOTTOM) return BottomLeft;
      if (info.getAnchor() == ToolWindowAnchor.RIGHT) return RightTop;
      /*if (info.getAnchor() == ToolWindowAnchor.TOP) */
      return TopLeft;
    }

    @NotNull
    public ToolWindowAnchor getAnchor() {
      return switch (this) {
        case LeftTop, LeftBottom -> ToolWindowAnchor.LEFT;
        case BottomLeft, BottomRight -> ToolWindowAnchor.BOTTOM;
        case RightBottom, RightTop -> ToolWindowAnchor.RIGHT;
        default -> ToolWindowAnchor.TOP;
      };
    }

    public boolean isSplit() {
      return Arrays.asList(LeftBottom, BottomRight, RightBottom, TopRight).contains(this);
    }

    @NotNull
    public Icon getIcon() {
      return switch (this) {
        case LeftTop -> AllIcons.Actions.MoveToLeftTop;
        case LeftBottom -> AllIcons.Actions.MoveToLeftBottom;
        case BottomLeft -> AllIcons.Actions.MoveToBottomLeft;
        case BottomRight -> AllIcons.Actions.MoveToBottomRight;
        case RightBottom -> AllIcons.Actions.MoveToRightBottom;
        case RightTop -> AllIcons.Actions.MoveToRightTop;
        case TopRight -> AllIcons.Actions.MoveToTopRight;
        default -> AllIcons.Actions.MoveToTopLeft;
      };
    }

    boolean isApplied(@NotNull ToolWindow window) {
      return getAnchor() == window.getAnchor() && window.isSplitMode() == isSplit();
    }

    public void applyTo(@NotNull ToolWindow window) {
      window.setAnchor(getAnchor(), null);
      window.setSplitMode(isSplit(), null);
    }
  }

  @Nullable
  private static ToolWindowManager getToolWindowManager(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    return (project == null || project.isDisposed()) ? null : ToolWindowManager.getInstance(project);
  }

  @Nullable
  public static ToolWindow getToolWindow(@NotNull AnActionEvent e) {
    ToolWindowManager manager = getToolWindowManager(e);
    if (manager == null) {
      return null;
    }

    ToolWindow window = e.getData(PlatformDataKeys.TOOL_WINDOW);
    if (window != null) {
      return window;
    }

    String id = manager.getActiveToolWindowId();
    return id == null ? null : manager.getToolWindow(id);
  }

  @NotNull
  private final Anchor myAnchor;

  public ToolWindowMoveAction(@NotNull Anchor anchor) {
    super(anchor.toString(), null, anchor.getIcon());
    myAnchor = anchor;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ToolWindow toolWindow = getToolWindow(e);
    if (toolWindow != null) {
      myAnchor.applyTo(toolWindow);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    ToolWindow toolWindow = getToolWindow(e);
    e.getPresentation().setVisible(toolWindow != null);
    e.getPresentation().setEnabled(toolWindow != null && !myAnchor.isApplied(toolWindow));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public @NotNull List<EventPair<?>> getAdditionalUsageData(@NotNull AnActionEvent event) {
    ToolWindow toolWindow = getToolWindow(event);
    if (toolWindow != null) {
      return Collections.singletonList(ToolwindowFusEventFields.TOOLWINDOW.with(toolWindow.getId()));
    }
    return Collections.emptyList();
  }

  public static final class Group extends DefaultActionGroup {
    private boolean isInitialized = false;
    public Group() {
      super(UIBundle.messagePointer("tool.window.move.to.action.group.name"), true);
    }

    @Override
    public boolean isDumbAware() {
      return true;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      if (!isInitialized) {
        if (ExperimentalUI.isNewUI()) {
          addAll(new ToolWindowMoveToAction.Group().getChildren(e));
        }
        else {
          for (Anchor anchor : Anchor.values()) {
            add(new ToolWindowMoveAction(anchor));
          }
        }
        isInitialized = true;
      }
      super.update(e);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }
  }
}
