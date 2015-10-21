/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Vladimir Kondratyev
 */
public final class ToolWindowsGroup extends ActionGroup implements DumbAware {

  private static final Comparator<ActivateToolWindowAction> COMPARATOR = new Comparator<ActivateToolWindowAction>() {
    public int compare(ActivateToolWindowAction a1, ActivateToolWindowAction a2) {
      int m1 = ActivateToolWindowAction.getMnemonicForToolWindow(a1.getToolWindowId());
      int m2 = ActivateToolWindowAction.getMnemonicForToolWindow(a2.getToolWindowId());

      if (m1 != -1 && m2 == -1) {
        return -1;
      }
      else if (m1 == -1 && m2 != -1) {
        return 1;
      }
      else if (m1 != -1) {
        return m1 - m2;
      }
      else {
      // Both actions have no mnemonic, therefore they are sorted alphabetically
        return a1.getToolWindowId().compareToIgnoreCase(a2.getToolWindowId());
      }
    }
  };

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(getEventProject(e) != null);
  }

  @NotNull
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    Project project = getEventProject(e);
    if (project == null) return EMPTY_ARRAY;
    List<ActivateToolWindowAction> result = getToolWindowActions(project, false);
    return result.toArray(new AnAction[result.size()]);
  }

  public static List<ActivateToolWindowAction> getToolWindowActions(@NotNull Project project, boolean shouldSkipHidden) {
    ActionManager actionManager = ActionManager.getInstance();
    ToolWindowManager manager = ToolWindowManager.getInstance(project);
    List<ActivateToolWindowAction> result = ContainerUtil.newArrayList();
    for (String id : manager.getToolWindowIds()) {
      if (shouldSkipHidden && !manager.getToolWindow(id).isShowStripeButton()) continue;
      String actionId = ActivateToolWindowAction.getActionIdForToolWindow(id);
      AnAction action = actionManager.getAction(actionId);
      if (action instanceof ActivateToolWindowAction) {
        result.add((ActivateToolWindowAction)action);
      }
    }
    Collections.sort(result, COMPARATOR);
    return result;
  }
}
