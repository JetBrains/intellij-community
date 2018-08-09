// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.util.Conditions.instanceOf;
import static com.intellij.openapi.util.Conditions.not;

public class QuickChangeViewModeAction extends QuickSwitchSchemeAction {
  @Override
  protected void fillActions(Project project, @NotNull DefaultActionGroup group, @NotNull DataContext dataContext) {
    for (AnAction child : getActions()) {
      group.add(child);
    }
  }

  @Override
  protected boolean isEnabled() {
    return getActions().length > 0;
  }

  private static AnAction[] getActions() {
    AnAction a = ActionManager.getInstance().getActionOrStub("ToggleFullScreenGroup");
    AnAction[] actions = a instanceof DefaultActionGroup
                         ? ((DefaultActionGroup)a).getChildActionsOrStubs()
                         : a instanceof ActionGroup ? ((ActionGroup)a).getChildren(null) : EMPTY_ARRAY;
    return ArrayUtil.toObjectArray(ContainerUtil.filter(actions, not(instanceOf(Separator.class))), AnAction.class);
  }
}
