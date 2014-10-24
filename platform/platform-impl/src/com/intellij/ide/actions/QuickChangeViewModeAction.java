/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.util.Conditions.instanceOf;
import static com.intellij.openapi.util.Conditions.not;

public class QuickChangeViewModeAction extends QuickSwitchSchemeAction {
  protected void fillActions(Project project, @NotNull DefaultActionGroup group, @NotNull DataContext dataContext) {
    for (AnAction child : getActions()) {
      group.add(child);
    }
  }

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
