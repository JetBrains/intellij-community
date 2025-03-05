// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.session.InlineCompletionContext
import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext

private class InlineCompletionActionsPromoter : ActionPromoter {
  override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction> {
    val editor = CommonDataKeys.EDITOR.getData(context) ?: return emptyList()

    actions.filterIsInstance<CallInlineCompletionAction>().takeIf { it.isNotEmpty() }?.let { return it }

    if (InlineCompletionContext.getOrNull(editor) == null) {
      return emptyList()
    }

    // Fixed order of actions' priority
    actions.filterIsInstance<InsertInlineCompletionAction>().takeIf { it.isNotEmpty() }?.let { return it }
    actions.filterIsInstance<SwitchInlineCompletionVariantAction>().takeIf { it.isNotEmpty() }?.let { return it }
    actions.filterIsInstance<InsertInlineCompletionWordAction>().takeIf { it.isNotEmpty() }?.let { return it }
    actions.filterIsInstance<InsertInlineCompletionLineAction>().takeIf { it.isNotEmpty() }?.let { return it }

    return emptyList()
  }
}
