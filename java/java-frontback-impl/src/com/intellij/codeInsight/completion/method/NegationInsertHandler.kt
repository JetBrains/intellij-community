// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.method

import com.intellij.codeInsight.completion.CodeCompletionFeatures
import com.intellij.codeInsight.completion.FrontendFriendlyInsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.method.JavaMethodCallInsertHandlerHelper.findInsertedCall
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.featureStatistics.FeatureUsageTracker
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
class NegationInsertHandler : FrontendFriendlyInsertHandler {
  override fun handleInsert(context: InsertionContext, item: LookupElement) {
    if (context.completionChar != '!') return
    val methodCall = findInsertedCall(item, context) ?: return

    //TODO IJPL-207762 where do we want statistics?
    FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EXCLAMATION_FINISH)

    context.setAddCompletionChar(false)

    context.document.insertString(methodCall.textRange.startOffset, "!")
  }
}