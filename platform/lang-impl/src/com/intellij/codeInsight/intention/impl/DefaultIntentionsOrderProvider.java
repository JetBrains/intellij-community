// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInsight.intention.PriorityAction.Priority;
import com.intellij.codeInspection.SuppressIntentionActionFromFix;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DefaultIntentionsOrderProvider implements IntentionsOrderProvider {

  @Override
  public @NotNull List<IntentionActionWithTextCaching> getSortedIntentions(@NotNull CachedIntentions context,
                                                                           @NotNull List<IntentionActionWithTextCaching> intentions) {
    return ContainerUtil.sorted(intentions, (o1, o2) -> {
      int weight1 = getWeight(context, o1);
      int weight2 = getWeight(context, o2);
      if (weight1 != weight2) {
        return weight2 - weight1;
      }
      return o1.compareTo(o2);
    });
  }

  public static int getPriorityWeight(@NotNull IntentionActionWithTextCaching action){
    IntentionAction nonDelegatedAction = findNonDelegatedAction(action.getAction());
    Priority priority = nonDelegatedAction instanceof PriorityAction ? ((PriorityAction)nonDelegatedAction).getPriority() : null;
    return getPriorityWeight(priority);
  }

  public static int getWeight(@NotNull CachedIntentions context, @NotNull IntentionActionWithTextCaching action) {
    final int group = context.getGroup(action).getPriority();
    IntentionAction nonDelegatedAction = findNonDelegatedAction(action.getAction());
    if (nonDelegatedAction instanceof PriorityAction) {
      return group + getPriorityWeight(((PriorityAction)nonDelegatedAction).getPriority());
    }
    if (nonDelegatedAction instanceof SuppressIntentionActionFromFix) {
      if (((SuppressIntentionActionFromFix)nonDelegatedAction).isShouldBeAppliedToInjectionHost() == ThreeState.NO) {
        return group - 1;
      }
    }
    return group;
  }

  private static IntentionAction findNonDelegatedAction(IntentionAction action) {
    while (action instanceof IntentionActionDelegate && !(action instanceof PriorityAction)) {
      action = ((IntentionActionDelegate)action).getDelegate();
    }
    return action;
  }

  public static int getPriorityWeight(@Nullable Priority priority) {
    if (priority == null) return 0;
    switch (priority) {
      case TOP:
        return 20;
      case HIGH:
        return 3;
      case LOW:
        return -3;
      case ERROR_FIX_LESS_IMPORTANT_THAN_INSPECTION_FIX:
        return IntentionGroup.INSPECTION.getPriority() - IntentionGroup.ERROR.getPriority() - 1;
      default:
        return 0;
    }
  }
}
