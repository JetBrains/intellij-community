// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
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
public final class NewActionGroup extends ActionGroup {
  private static final @NonNls String PROJECT_OR_MODULE_GROUP_ID = "NewProjectOrModuleGroup";

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

  /** @deprecated Avoid explicit synchronous group expansion! */
  @Deprecated(forRemoval = true)
  public static boolean isActionInNewPopupMenu(@NotNull AnAction action) {
    ActionManager actionManager = ActionManager.getInstance();
    ActionGroup fileGroup = (ActionGroup)actionManager.getAction(IdeActions.GROUP_FILE);
    if (!anyActionFromGroupMatches(fileGroup, false, child -> child instanceof NewActionGroup)) return false;

    AnAction newProjectOrModuleGroup = ActionManager.getInstance().getAction(PROJECT_OR_MODULE_GROUP_ID);
    if (newProjectOrModuleGroup instanceof ActionGroup
        && anyActionFromGroupMatches((ActionGroup)newProjectOrModuleGroup, false, Predicate.isEqual(action))) {
      return true;
    }

    ActionGroup newGroup = (ActionGroup)actionManager.getAction(IdeActions.GROUP_NEW);
    return anyActionFromGroupMatches(newGroup, false, Predicate.isEqual(action));
  }

  /** @deprecated Avoid explicit synchronous group expansion! */
  @Deprecated(forRemoval = true)
  public static boolean anyActionFromGroupMatches(@NotNull ActionGroup group, boolean processPopupSubGroups,
                                                  @NotNull Predicate<? super AnAction> condition) {
    for (AnAction child : group.getChildren(null)) {
      if (condition.test(child)) return true;
      if (child instanceof ActionGroup o) {
        if ((processPopupSubGroups || !o.isPopup()) && anyActionFromGroupMatches(o, processPopupSubGroups, condition)) {
          return true;
        }
      }
    }
    return false;
  }
}
