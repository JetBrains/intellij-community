// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.inplace

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.template.Expression
import com.intellij.codeInsight.template.ExpressionContext
import com.intellij.codeInsight.template.Result
import com.intellij.codeInsight.template.TextResult
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.refactoring.rename.api.UsageTextByName

internal open class InplaceRenameUsageExpression(
  private val usageTextByName: UsageTextByName
) : Expression() {

  final override fun requiresCommittedPSI(): Boolean = false

  final override fun calculateLookupItems(context: ExpressionContext?): Array<LookupElement> = LookupElement.EMPTY_ARRAY

  override fun calculateResult(context: ExpressionContext): Result? {
    val editor = context.editor ?: return null
    val state = TemplateManagerImpl.getTemplateState(editor) ?: return null
    val newName = state.getNewName()
    val newText = usageTextByName(newName) ?: ""
    return TextResult(newText)
  }
}
