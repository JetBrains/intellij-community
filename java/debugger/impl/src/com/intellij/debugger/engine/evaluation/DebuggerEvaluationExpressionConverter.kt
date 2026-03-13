// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.evaluation

import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

/**
 * Creates a [TextWithImports] for the target language from a Java [PsiElement] produced by the debugger tree evaluation chain.
 */
@ApiStatus.Internal
interface DebuggerEvaluationExpressionConverter {
  fun convert(psiExpression: PsiElement): TextWithImports?

  companion object {
    private val EP_NAME: LanguageExtension<DebuggerEvaluationExpressionConverter> =
      LanguageExtension("com.intellij.debugger.evaluationExpressionConverter")

    @JvmStatic
    fun forLanguage(language: Language): DebuggerEvaluationExpressionConverter? = EP_NAME.forLanguage(language)
  }
}
