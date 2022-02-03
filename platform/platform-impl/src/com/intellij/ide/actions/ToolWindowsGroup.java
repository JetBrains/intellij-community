// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static java.lang.String.CASE_INSENSITIVE_ORDER;
import static java.util.Comparator.comparingInt;

/**
 * @author Vladimir Kondratyev
 */
public final class ToolWindowsGroup extends ActionGroup implements DumbAware {
  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(getEventProject(e) != null);
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    Project project = getEventProject(e);
    if (project == null) return EMPTY_ARRAY;
    List<ActivateToolWindowAction> result = getToolWindowActions(project, false);
    return result.toArray(AnAction.EMPTY_ARRAY);
  }

  public static List<ActivateToolWindowAction> getToolWindowActions(@NotNull Project project, boolean shouldSkipHidden) {
    ActionManager actionManager = ActionManager.getInstance();
    ToolWindowManagerEx manager = ToolWindowManagerEx.getInstanceEx(project);
    List<ActivateToolWindowAction> result = new ArrayList<>();
    for (ToolWindow window : manager.getToolWindows()) {
      if (shouldSkipHidden && !window.isShowStripeButton()) {
        continue;
      }
      String actionId = ActivateToolWindowAction.getActionIdForToolWindow(window.getId());
      AnAction action = actionManager.getAction(actionId);
      if (action instanceof ActivateToolWindowAction) {
        result.add((ActivateToolWindowAction)action);
      }
    }
    AnAction activateGroup = actionManager.getAction("ActivateToolWindowActions");
    if (activateGroup instanceof ActionGroup) {
      AnAction[] children = ((DefaultActionGroup)activateGroup).getChildren(null);
      for (AnAction child : children) {
        if (child instanceof ActivateToolWindowAction && !result.contains(child)) {
          result.add((ActivateToolWindowAction) child);
        }
      }
    }
    result.sort(getActionComparator());
    return result;
  }

  @NotNull
  private static Comparator<ActivateToolWindowAction> getActionComparator() {
    return comparingMnemonic().thenComparing(it -> it.getToolWindowId(), CASE_INSENSITIVE_ORDER);
  }

  @NotNull
  private static Comparator<ActivateToolWindowAction> comparingMnemonic() {
    return comparingInt(it -> {
      int mnemonic = ActivateToolWindowAction.getMnemonicForToolWindow(it.getToolWindowId());
      return mnemonic != -1 ? mnemonic : Integer.MAX_VALUE;
    });
  }
}
