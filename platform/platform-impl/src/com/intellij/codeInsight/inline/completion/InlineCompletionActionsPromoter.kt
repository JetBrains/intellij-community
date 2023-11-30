// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion

import com.intellij.codeInsight.inline.completion.session.InlineCompletionContext
import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext

private class InlineCompletionActionsPromoter : ActionPromoter {
  override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction> {
    val editor = CommonDataKeys.EDITOR.getData(context) ?: return emptyList()

    if (InlineCompletionContext.getOrNull(editor) == null) {
      return emptyList()
    }

    return actions.filterIsInstance<InsertInlineCompletionAction>()
  }
}
