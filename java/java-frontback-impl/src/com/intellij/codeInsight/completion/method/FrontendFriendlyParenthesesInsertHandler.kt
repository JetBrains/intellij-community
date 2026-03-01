// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.method

import com.intellij.codeInsight.completion.FrontendFriendlyInsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.JavaFrontendCompletionUtil
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.util.ThreeState
import kotlinx.serialization.Serializable

@Serializable
class FrontendFriendlyParenthesesInsertHandler(
  private val hasParameters: Boolean,
  private val isVoidMethod: Boolean,
) : FrontendFriendlyInsertHandler {
  override fun handleInsert(context: InsertionContext, item: LookupElement) {
    // todo FrontendFriendlyParenthInsertHandler differs from ParenthInsertHandler
    //      in that it does not check lookup elements for overloads with `MethodParenthesesHandler.overloadsHaveParameters`
    //      not sure if this can be reliably implemented on frontend
    insertParenthesesForJavaMethod(item, context, ThreeState.fromBoolean(hasParameters), isVoidMethod)
  }

  companion object {
    fun insertParenthesesForJavaMethod(
      item: LookupElement,
      context: InsertionContext,
      hasParams: ThreeState,
      isVoidMethod: Boolean,
    ) {
      JavaFrontendCompletionUtil.insertParentheses(context, item, false, hasParams, false, isVoidMethod)
    }
  }
}