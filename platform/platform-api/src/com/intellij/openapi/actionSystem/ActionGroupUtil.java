// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.actionSystem;

import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.util.containers.JBTreeTraverser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class ActionGroupUtil {

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
