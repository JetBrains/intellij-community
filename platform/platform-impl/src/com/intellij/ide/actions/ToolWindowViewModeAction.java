// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.idea.ActionsBundle;
import com.intellij.internal.statistic.eventLog.events.EventPair;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.FusAwareAction;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.WindowInfo;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

@SuppressWarnings("ComponentNotRegistered")
public class ToolWindowViewModeAction extends DumbAwareToggleAction implements FusAwareAction {
  public enum ViewMode {
    DockPinned("DockPinnedMode"),
    DockUnpinned("DockUnpinnedMode"),
    Undock("UndockMode"),
    Float("FloatMode"),
    Window("WindowMode");

    private final String myActionID;

    ViewMode(@NonNls String actionID) {
      myActionID = actionID;
    }

    public String getActionID() {
      return myActionID;
    }

    boolean isApplied(@NotNull ToolWindow window) {
      ToolWindowType type = window.getType();
      return switch (this) {
        case DockPinned -> type == ToolWindowType.DOCKED && !window.isAutoHide();
        case DockUnpinned -> type == ToolWindowType.DOCKED && window.isAutoHide();
        case Undock -> type == ToolWindowType.SLIDING;
        case Float -> type == ToolWindowType.FLOATING;
        case Window -> type == ToolWindowType.WINDOWED;
      };
    }

    public static ViewMode fromWindowInfo(@NotNull WindowInfo info) {
      return switch (info.getType()) {
        case DOCKED -> info.isAutoHide() ? DockUnpinned : DockPinned;
        case FLOATING -> Float;
        case SLIDING -> Undock;
        case WINDOWED -> Window;
      };
    }

    void applyTo(@NotNull ToolWindow window) {
      switch (this) {
        case DockPinned -> {
          window.setType(ToolWindowType.DOCKED, null);
          window.setAutoHide(false);
        }
        case DockUnpinned -> {
          window.setType(ToolWindowType.DOCKED, null);
          window.setAutoHide(true);
        }
        case Undock -> window.setType(ToolWindowType.SLIDING, null);
        case Float -> window.setType(ToolWindowType.FLOATING, null);
        case Window -> window.setType(ToolWindowType.WINDOWED, null);
      }
    }
  }

  @NotNull protected final ViewMode myMode;

  protected ToolWindowViewModeAction(@NotNull ViewMode mode) {
    myMode = mode;
    getTemplatePresentation().setText(ActionsBundle.actionText(myMode.myActionID));
    getTemplatePresentation().setDescription(ActionsBundle.actionDescription(myMode.myActionID));
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

  @Override
  public final boolean isSelected(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null || project.isDisposed()) {
      return false;
    }
    ToolWindow window = getToolWindow(e);
    return (window != null) && isSelected(window);
  }

  protected boolean isSelected(@NotNull ToolWindow window) {
    return myMode.isApplied(window);
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    ToolWindow window = getToolWindow(e);
    if (window == null) {
      return;
    }
    if (!myMode.isApplied(window)) {
      myMode.applyTo(window);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabledAndVisible(getToolWindow(e) != null);
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

  public static class Group extends DefaultActionGroup {
    private boolean isInitialized = false;

    @Override
    public boolean isDumbAware() {
      return true;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      if (!isInitialized) {
        ActionManager actionManager = ActionManager.getInstance();
        for (ToolWindowViewModeAction.ViewMode viewMode : ToolWindowViewModeAction.ViewMode.values()) {
          ToolWindowViewModeAction action = new ToolWindowViewModeAction(viewMode);
          AnAction template = actionManager.getAction(viewMode.getActionID());
          if (template != null) {
            action.copyShortcutFrom(template);
          }
          add(action);
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
