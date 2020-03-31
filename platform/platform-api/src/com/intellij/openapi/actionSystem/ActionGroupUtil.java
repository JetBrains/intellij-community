/*
 * Copyright 2000-2019 JetBrains s.r.o.
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
import com.intellij.util.containers.JBTreeTraverser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class ActionGroupUtil {

  /**
   * @deprecated use {@link #isGroupEmpty(ActionGroup, AnActionEvent, boolean)} instead
   */
  @Deprecated
  public static boolean isGroupEmpty(@NotNull ActionGroup actionGroup, @NotNull AnActionEvent e) {
    return isGroupEmpty(actionGroup, e, false);
  }

  public static boolean isGroupEmpty(@NotNull ActionGroup actionGroup, @NotNull AnActionEvent e, boolean isInModalContext) {
    return activeActionTraverser(actionGroup, e, isInModalContext).traverse().isEmpty();
  }

  @Nullable
  public static AnAction getSingleActiveAction(@NotNull ActionGroup actionGroup, @NotNull AnActionEvent e, boolean isInModalContext) {
    return activeActionTraverser(actionGroup, e, isInModalContext).traverse().single();
  }

  @NotNull
  public static JBTreeTraverser<AnAction> activeActionTraverser(@NotNull ActionGroup actionGroup,
                                                                @NotNull AnActionEvent e,
                                                                boolean isInModalContext) {
    Map<AnAction, Presentation> action2presentation = new HashMap<>();
    return JBTreeTraverser.<AnAction>of(
      o -> o instanceof ActionGroup &&
           isActionEnabledAndVisible(o, e, isInModalContext, action2presentation) &&
           !((ActionGroup)o).isPopup() &&
           !((ActionGroup)o).canBePerformed(e.getDataContext()) ? ((ActionGroup)o).getChildren(e) : null)
      .withRoots(actionGroup.getChildren(e))
      .filter(o -> !(o instanceof Separator) &&
                   (o instanceof ActionGroup || isActionEnabledAndVisible(o, e, isInModalContext, action2presentation)));
  }

  private static boolean isActionEnabledAndVisible(@NotNull AnAction action,
                                                   @NotNull AnActionEvent e,
                                                   boolean isInModalContext,
                                                   @NotNull Map<AnAction, Presentation> presentationMap) {
    Presentation presentation = presentationMap.computeIfAbsent(action, k -> action.getTemplatePresentation().clone());
    return isActionEnabledAndVisible(action, e, isInModalContext, presentation);
  }

  public static boolean isActionEnabledAndVisible(@NotNull AnAction action,
                                                  @NotNull AnActionEvent e,
                                                  boolean isInModalContext) {
    Presentation presentation = action.getTemplatePresentation().clone();
    return isActionEnabledAndVisible(action, e, isInModalContext, presentation);
  }

  private static boolean isActionEnabledAndVisible(@NotNull AnAction action,
                                                   @NotNull AnActionEvent e,
                                                   boolean isInModalContext,
                                                   @NotNull Presentation presentation) {
    AnActionEvent event = new AnActionEvent(
      e.getInputEvent(), e.getDataContext(), e.getPlace(),
      presentation, ActionManager.getInstance(), e.getModifiers());
    event.setInjectedContext(action.isInInjectedContext());
    ActionUtil.performDumbAwareUpdate(isInModalContext, action, event, false);

    return presentation.isEnabled() && presentation.isVisible();
  }
}
