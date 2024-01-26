// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInsight.intention.PriorityAction.Priority;
import com.intellij.codeInspection.SuppressIntentionActionFromFix;
import com.intellij.util.ThreeState;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class DefaultIntentionsOrderProvider implements IntentionsOrderProvider {

  @Override
  public @NotNull List<IntentionActionWithTextCaching> getSortedIntentions(@NotNull CachedIntentions context,
                                                                           @NotNull List<IntentionActionWithTextCaching> intentions) {
    return StreamEx.of(intentions)
      .mapToEntry(intention -> getWeight(context, intention))
      .sorted((weightedIntention1, weightedIntention2) -> {
        int intentionWeightComparison = Integer.compare(weightedIntention1.getValue(), weightedIntention2.getValue());
        if (intentionWeightComparison != 0) return -intentionWeightComparison; //desc sorting for weight
        return weightedIntention1.getKey().compareTo(weightedIntention2.getKey()); //asc sorting for text
      })
      .keys()
      .toList();
  }

  public static int getPriorityWeight(@NotNull IntentionActionWithTextCaching action) {
    IntentionAction nonDelegatedAction = findNonDelegatedAction(action.getAction());
    Priority priority = nonDelegatedAction instanceof PriorityAction ? ((PriorityAction)nonDelegatedAction).getPriority() : null;
    return getPriorityWeight(priority);
  }

  public static int getWeight(@NotNull CachedIntentions context, @NotNull IntentionActionWithTextCaching action) {
    int group = context.getGroup(action).getPriority();
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
    return switch (priority) {
      case TOP -> 20;
      case HIGH -> 3;
      case LOW -> -3;
      case BOTTOM -> -20;
      default -> 0;
    };
  }
}
