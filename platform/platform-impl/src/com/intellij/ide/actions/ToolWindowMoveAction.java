// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.internal.statistic.eventLog.events.EventPair;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.FusAwareAction;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.WindowInfo;
import com.intellij.openapi.wm.impl.SquareStripeButton;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.toolWindow.ToolWindowDragHelper;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.UIBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ToolWindowMoveAction extends DumbAwareAction implements FusAwareAction, ActionRemoteBehaviorSpecification.Frontend {
  public enum Anchor {
    LeftTop, LeftBottom, BottomLeft, BottomRight, RightBottom, RightTop, TopRight, TopLeft;

    @Override
    public @Nls String toString() {
      String top = UIBundle.message("tool.window.move.to.top.action.name");
      String left = UIBundle.message("tool.window.move.to.left.action.name");
      String bottom = UIBundle.message("tool.window.move.to.bottom.action.name");
      String right = UIBundle.message("tool.window.move.to.right.action.name");
      return switch (this) {
        case LeftTop     -> left   + " " + top;
        case BottomLeft  -> bottom + " " + left;
        case BottomRight -> bottom + " " + right;
        case RightTop    -> right  + " " + top;
        case LeftBottom  -> left   + " " + bottom;
        case RightBottom -> right  + " " + bottom;
        case TopRight    -> top    + " " + right;
        case TopLeft     -> top    + " " + left;
      };
    }

    public static @NotNull Anchor fromWindowInfo(@NotNull WindowInfo info) {
      return getAnchor(info.getAnchor(), info.isSplit());
    }

    public static @NotNull Anchor getAnchor(@NotNull ToolWindowAnchor anchor, boolean split) {
      if (split) {
        if (anchor == ToolWindowAnchor.LEFT) return LeftBottom;
        if (anchor == ToolWindowAnchor.BOTTOM) return BottomRight;
        if (anchor == ToolWindowAnchor.RIGHT) return RightBottom;
        if (anchor == ToolWindowAnchor.TOP) return TopRight;
      }

      if (anchor == ToolWindowAnchor.LEFT) return LeftTop;
      if (anchor == ToolWindowAnchor.BOTTOM) return BottomLeft;
      if (anchor == ToolWindowAnchor.RIGHT) return RightTop;
      /*if (anchor == ToolWindowAnchor.TOP) */
      return TopLeft;
    }

    public @NotNull ToolWindowAnchor getAnchor() {
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

    public @NotNull Icon getIcon() {
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
      applyTo(window, -1);
    }

    public void applyTo(@NotNull ToolWindow window, int order) {
      if (window instanceof ToolWindowImpl toolWindow) {
        toolWindow.setSideToolAndAnchor(getAnchor(), isSplit(), order);
      }
      else {
        window.setAnchor(getAnchor(), null);
        window.setSplitMode(isSplit(), null);
      }
    }
  }

  private static @Nullable ToolWindowManager getToolWindowManager(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    return (project == null || project.isDisposed()) ? null : ToolWindowManager.getInstance(project);
  }

  public static @Nullable ToolWindow getToolWindow(@NotNull AnActionEvent e) {
    ToolWindowManager manager = getToolWindowManager(e);
    if (manager == null) {
      return null;
    }

    ToolWindow window = e.getData(PlatformDataKeys.TOOL_WINDOW);
    if (window != null) {
      return window;
    }

    Component component = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
    if (component instanceof SquareStripeButton) {
      return ((SquareStripeButton)component).getToolWindow();
    }
    if (component instanceof ToolWindowDragHelper.ToolWindowProvider twp) {
      return twp.getToolWindow();
    }
    String id = manager.getActiveToolWindowId();
    return id == null ? null : manager.getToolWindow(id);
  }

  private final @NotNull Anchor myAnchor;

  public ToolWindowMoveAction(@NotNull Anchor anchor) {
    super(() -> anchor.toString(), null, () -> anchor.getIcon());

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

  public static final class Group extends DefaultActionGroup implements DumbAware {
    public Group() {
      super(UIBundle.messagePointer("tool.window.move.to.action.group.name"), true);
      addAll(generateActions());
    }

    private static @NotNull List<? extends AnAction> generateActions() {
      return Arrays.stream(Anchor.values())
        .filter(x -> isAllowed(x))
        .map(x -> new ToolWindowMoveAction(x))
        .toList();
    }

    private static boolean isAllowed(Anchor anchor) {
      if (ExperimentalUI.isNewUI()) {
        return anchor != Anchor.TopLeft && anchor != Anchor.TopRight;
      }

      return true;
    }
  }
}
