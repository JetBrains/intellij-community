// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.inplace

import com.intellij.codeInsight.template.ExpressionContext
import com.intellij.codeInsight.template.Result
import com.intellij.codeInsight.template.TextResult
import com.intellij.refactoring.rename.api.UsageTextByName

internal class InplaceRenameTextUsageExpression(
  usageTextByName: UsageTextByName,
  private val originalText: String,
  private val isActive: () -> Boolean,
) : InplaceRenameUsageExpression(usageTextByName) {

  override fun calculateResult(context: ExpressionContext): Result? {
    if (isActive()) {
      return super.calculateResult(context)
    }
    else {
      return TextResult(originalText)
    }
  }
}
