/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.openapi.actionSystem;

import com.intellij.openapi.actionSystem.ex.ActionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActionGroupUtil {
  private static Presentation getPresentation(AnAction action, Map<AnAction, Presentation> action2presentation) {
    return action2presentation.computeIfAbsent(action, k -> action.getTemplatePresentation().clone());
  }

  @Deprecated
  // Use #isGroupEmpty with isModalContext instead
  public static boolean isGroupEmpty(@NotNull ActionGroup actionGroup, @NotNull AnActionEvent e) {
    return isGroupEmpty(actionGroup, e, new HashMap<>(), false);
  }

  public static boolean isGroupEmpty(@NotNull ActionGroup actionGroup, @NotNull AnActionEvent e, boolean isInModalContext) {
    return isGroupEmpty(actionGroup, e, new HashMap<>(), isInModalContext);
  }

  private static boolean isGroupEmpty(@NotNull ActionGroup actionGroup,
                                                   @NotNull AnActionEvent e,
                                                   @NotNull Map<AnAction, Presentation> action2presentation,
                                                   boolean inModalContext) {
    AnAction[] actions = actionGroup.getChildren(e);
    for (AnAction action : actions) {
      if (action instanceof Separator) continue;
      if (isActionEnabledAndVisible(action, e, inModalContext, action2presentation)) {
        if (action instanceof ActionGroup) {
          if (!isGroupEmpty((ActionGroup)action, e, action2presentation, inModalContext)) {
            return false;
          }
          // else continue for-loop
        }
        else {
          return false;
        }
      }
    }
    return true;
  }

  @Nullable
  public static AnAction getSingleActiveAction(@NotNull ActionGroup actionGroup, @NotNull AnActionEvent e, boolean isInModalContext) {
    List<AnAction> children = getEnabledChildren(actionGroup, e, new HashMap<>(), isInModalContext);
    if (children.size() == 1) {
      return children.get(0);
    }
    return null;
  }

  private static List<AnAction> getEnabledChildren(@NotNull ActionGroup actionGroup,
                                                   @NotNull AnActionEvent e,
                                                   @NotNull Map<AnAction, Presentation> action2presentation,
                                                   boolean isInModalContext) {
    List<AnAction> result = new ArrayList<>();
    AnAction[] actions = actionGroup.getChildren(e);
    for (AnAction action : actions) {
      if (action instanceof ActionGroup) {
        if (isActionEnabledAndVisible(action, e, isInModalContext, action2presentation)) {
          result.addAll(getEnabledChildren((ActionGroup)action, e, action2presentation, isInModalContext));
        }
      }
      else if (!(action instanceof Separator)) {
        if (isActionEnabledAndVisible(action, e, isInModalContext, action2presentation)) {
          result.add(action);
        }
      }
    }
    return result;
  }

  private static boolean isActionEnabledAndVisible(@NotNull AnAction action,
                                                   @NotNull AnActionEvent e,
                                                   boolean isInModalContext,
                                                   @NotNull Map<AnAction, Presentation> action2presentation) {
    Presentation presentation = getPresentation(action, action2presentation);
    AnActionEvent event = new AnActionEvent(
      e.getInputEvent(), e.getDataContext(), e.getPlace(),
      presentation, ActionManager.getInstance(), e.getModifiers());
    event.setInjectedContext(action.isInInjectedContext());
    ActionUtil.performDumbAwareUpdate(isInModalContext, action, event, false);

    return presentation.isEnabled() && presentation.isVisible();
  }
}
