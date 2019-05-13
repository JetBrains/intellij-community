// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.UIBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;

public class ToolWindowMoveAction extends DumbAwareAction {

  public enum Anchor {
    LeftTop, LeftBottom, BottomLeft, BottomRight, RightBottom, RightTop, TopRight, TopLeft;

    @Override
    public String toString() {
      String top = UIBundle.message("tool.window.move.to.top.action.name");
      String left = UIBundle.message("tool.window.move.to.left.action.name");
      String bottom = UIBundle.message("tool.window.move.to.bottom.action.name");
      String right = UIBundle.message("tool.window.move.to.right.action.name");
      switch (this) {
        case LeftTop:
          return left + " " + top;
        case LeftBottom:
          return left + " " + bottom;
        case BottomLeft:
          return bottom + " " + left;
        case BottomRight:
          return bottom + " " + right;
        case RightBottom:
          return right + " " + bottom;
        case RightTop:
          return right + " " + top;
        case TopRight:
          return top + " " + right;
        case TopLeft:
          return top + " " + left;
      }
      return super.toString();
    }
    @NotNull
    private ToolWindowAnchor getAnchor() {
      switch (this) {
        case LeftTop:
        case LeftBottom:
          return ToolWindowAnchor.LEFT;
        case BottomLeft:
        case BottomRight:
          return ToolWindowAnchor.BOTTOM;
        case RightBottom:
        case RightTop:
          return ToolWindowAnchor.RIGHT;
        default:
          return ToolWindowAnchor.TOP;
      }
    }

    private boolean isSplit() {
      return Arrays.asList(LeftBottom, BottomRight, RightBottom, TopRight).contains(this);
    }
    @NotNull
    private Icon getIcon() {
      switch (this) {
        case LeftTop:return AllIcons.Actions.MoveToLeftTop;
        case LeftBottom:return AllIcons.Actions.MoveToLeftBottom;
        case BottomLeft:return AllIcons.Actions.MoveToBottomLeft;
        case BottomRight:return AllIcons.Actions.MoveToBottomRight;
        case RightBottom:return AllIcons.Actions.MoveToRightBottom;
        case RightTop:return AllIcons.Actions.MoveToRightTop;
        case TopRight:return AllIcons.Actions.MoveToTopRight;
        default:return AllIcons.Actions.MoveToTopLeft;
      }
    }

    boolean isApplied(@NotNull ToolWindow window) {
      return getAnchor() == window.getAnchor() && window.isSplitMode() == isSplit();
    }

    void applyTo(@NotNull ToolWindow window) {
      window.setAnchor(getAnchor(), null);
      window.setSplitMode(isSplit(), null);
    }
  }

  @Nullable
  protected ToolWindowManager getToolWindowManager(AnActionEvent e) {
    Project project = e.getProject();
    return project == null || project.isDisposed()
           ? null
           : ToolWindowManager.getInstance(project);
  }

  @Nullable
  protected ToolWindow getToolWindow(AnActionEvent e) {
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
  @NotNull private final Anchor myAnchor;

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
    e.getPresentation().setEnabled(toolWindow != null && !myAnchor.isApplied(toolWindow));
  }


  public static class Group extends DefaultActionGroup {
    private boolean isInitialized = false;
    public Group() {
      super(UIBundle.message("tool.window.move.to.action.group.name"), true);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      if (!isInitialized) {
        for (ToolWindowMoveAction.Anchor anchor : ToolWindowMoveAction.Anchor.values()) {
          add(new ToolWindowMoveAction(anchor));
        }
        isInitialized = true;
      }
      super.update(e);
    }
  }
}
