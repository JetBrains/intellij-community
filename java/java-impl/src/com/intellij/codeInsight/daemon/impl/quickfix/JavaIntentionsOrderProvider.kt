// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix

import com.intellij.codeInsight.intention.impl.CachedIntentions
import com.intellij.codeInsight.intention.impl.DefaultIntentionsOrderProvider
import com.intellij.codeInsight.intention.impl.IntentionActionWithTextCaching
import com.intellij.codeInsight.intention.impl.IntentionsOrderProvider

public class JavaIntentionsOrderProvider : IntentionsOrderProvider {
  override fun getSortedIntentions(context: CachedIntentions, intentions: List<IntentionActionWithTextCaching>): List<IntentionActionWithTextCaching> {
    val (errors, others) = intentions.partition { intention -> isCompilationFix(context, intention) }
    val errorsSortedByPriority = errors
      .groupBy { error -> DefaultIntentionsOrderProvider.getPriorityWeight(error) }
      .entries.sortedByDescending { (weight, _) -> weight }
      .flatMap { (_, groups) -> groups }
    return errorsSortedByPriority + DefaultIntentionsOrderProvider().getSortedIntentions(context, others)
  }

  private fun isCompilationFix(context: CachedIntentions, intention: IntentionActionWithTextCaching): Boolean {
    if (intention.fixRange?.containsOffset(context.offset) == false) return false
    val isInspectionHighlighting = context.highlightInfoType?.isInspectionHighlightInfoType == true
    return context.errorFixes.contains(intention) && !isInspectionHighlighting
  }
}