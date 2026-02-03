// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.util.Conditions.instanceOf;
import static com.intellij.openapi.util.Conditions.not;

@ApiStatus.Internal
public final class QuickChangeViewModeAction extends QuickSwitchSchemeAction implements ActionRemoteBehaviorSpecification.Frontend {
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

    AnAction presentationAssistant = ActionManager.getInstance().getActionOrStub("TogglePresentationAssistantAction");
    if (presentationAssistant != null) {
      actions = ArrayUtil.append(actions, presentationAssistant);
    }

    return ContainerUtil.filter(actions, not(instanceOf(Separator.class))).toArray(AnAction.EMPTY_ARRAY);
  }
}
