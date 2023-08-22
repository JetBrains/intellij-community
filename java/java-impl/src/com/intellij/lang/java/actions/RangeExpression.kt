// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.template.Expression
import com.intellij.codeInsight.template.ExpressionContext
import com.intellij.codeInsight.template.Result
import com.intellij.codeInsight.template.TextResult
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.util.TextRange

/**
 * This expression copies text from the specified range.
 */
internal class RangeExpression(
  private val document: Document,
  range: TextRange
) : Expression() {

  private val marker: RangeMarker = document.createRangeMarker(range).also {
    it.isGreedyToLeft = true
    it.isGreedyToRight = true
  }

  val text: String get() = document.getText(marker.textRange)

  override fun calculateResult(context: ExpressionContext): Result = TextResult(text)

  override fun requiresCommittedPSI(): Boolean = false

  override fun calculateLookupItems(context: ExpressionContext): Array<out LookupElement> = LookupElement.EMPTY_ARRAY
}
