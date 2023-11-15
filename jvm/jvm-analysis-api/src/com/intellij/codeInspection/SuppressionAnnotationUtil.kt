// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection

import com.intellij.lang.LanguageExtension
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UExpression

private val EP_NAME: ExtensionPointName<SuppressionAnnotationUtil> =
  ExtensionPointName.create("com.intellij.codeInspection.suppressionAnnotationUtil")

interface SuppressionAnnotationUtil {
  companion object {
    @JvmField
    val extension = LanguageExtension<SuppressionAnnotationUtil>(EP_NAME.name)
  }

  fun isSuppressionAnnotation(annotation: UAnnotation): Boolean
  fun getSuppressionAnnotationAttributeExpressions(annotation: UAnnotation): List<UExpression>
  fun getRemoveAnnotationQuickFix(annotation: PsiElement): LocalQuickFix?
}
