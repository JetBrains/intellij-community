// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleSettings
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
open class IdeJavaFoldingBuilderBase: JavaFoldingBuilderBase() {
  override fun isBelowRightMargin(file: PsiFile, lineLength: Int): Boolean {
    val settings: CodeStyleSettings = com.intellij.application.options.CodeStyle.getSettings(file)
    return lineLength <= settings.getRightMargin(JavaLanguage.INSTANCE)
  }

  protected open override fun shouldShowExplicitLambdaType(anonymousClass: PsiAnonymousClass, expression: PsiNewExpression): Boolean {
    val parent = expression.getParent()
    return parent is PsiReferenceExpression || parent is PsiAssignmentExpression
  }

  protected override fun rightArrow(): String {
    return getRightArrow()
  }

  companion object {
    @JvmStatic
    fun getRightArrow(): String {
      return EditorUtil.displayCharInEditor('\u2192', EditorColors.FOLDED_TEXT_ATTRIBUTES, "->")
    }
  }
}