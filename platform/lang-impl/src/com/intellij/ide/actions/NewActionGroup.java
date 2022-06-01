/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * @author Dmitry Avdeev
 */
public class NewActionGroup extends ActionGroup {
  @NonNls private static final String PROJECT_OR_MODULE_GROUP_ID = "NewProjectOrModuleGroup";

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    AnAction g1 = ActionManager.getInstance().getAction(IdeActions.GROUP_WEIGHING_NEW);
    AnAction g2 = ActionManager.getInstance().getAction(PROJECT_OR_MODULE_GROUP_ID);
    ActionUpdateThread t1 = g1 == null ? null : g1.getActionUpdateThread();
    ActionUpdateThread t2 = g2 == null ? null : g2.getActionUpdateThread();
    if (t1 == null && t2 == null) return ActionUpdateThread.BGT;
    if (t1 != null && t2 != null && t1 != t2) throw new AssertionError(t1 + "!=" + t2);
    return Objects.requireNonNullElse(t1, t2);
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    boolean addG2 = e == null || ActionPlaces.isMainMenuOrActionSearch(e.getPlace());
    AnAction g1 = ActionManager.getInstance().getAction(IdeActions.GROUP_WEIGHING_NEW);
    AnAction g2 = addG2 ? ActionManager.getInstance().getAction(PROJECT_OR_MODULE_GROUP_ID) : null;

    AnAction[] actions1 = g1 instanceof ActionGroup ? ((ActionGroup)g1).getChildren(e) : EMPTY_ARRAY;
    AnAction[] actions2 = g2 instanceof ActionGroup ? ((ActionGroup)g2).getChildren(e) : EMPTY_ARRAY;
    if (actions2.length == 0) return actions1;

    List<AnAction> mergedActions = new ArrayList<>(actions2.length + 1 + actions1.length);
    Collections.addAll(mergedActions, actions2);
    mergedActions.add(Separator.getInstance());
    Collections.addAll(mergedActions, actions1);
    return mergedActions.toArray(AnAction.EMPTY_ARRAY);
  }

  public static boolean isActionInNewPopupMenu(@NotNull AnAction action) {
    ActionManager actionManager = ActionManager.getInstance();
    ActionGroup fileGroup = (ActionGroup)actionManager.getAction(IdeActions.GROUP_FILE);
    if (!ActionUtil.anyActionFromGroupMatches(fileGroup, false, child -> child instanceof NewActionGroup)) return false;

    AnAction newProjectOrModuleGroup = ActionManager.getInstance().getAction(PROJECT_OR_MODULE_GROUP_ID);
    if (newProjectOrModuleGroup instanceof ActionGroup
        && ActionUtil.anyActionFromGroupMatches((ActionGroup)newProjectOrModuleGroup, false,Predicate.isEqual(action))) {
      return true;
    }

    ActionGroup newGroup = (ActionGroup)actionManager.getAction(IdeActions.GROUP_NEW);
    return ActionUtil.anyActionFromGroupMatches(newGroup, false, Predicate.isEqual(action));
  }
}
