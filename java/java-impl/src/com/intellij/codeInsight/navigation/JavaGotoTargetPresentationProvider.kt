// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation

import com.intellij.ide.util.PsiClassRenderingInfo
import com.intellij.ide.util.PsiElementRenderingInfo
import com.intellij.ide.util.PsiFunctionalExpressionRenderingInfo
import com.intellij.ide.util.PsiMethodRenderingInfo
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFunctionalExpression
import com.intellij.psi.PsiMethod

public class JavaGotoTargetPresentationProvider : GotoTargetPresentationProvider {

  override fun getTargetPresentation(element: PsiElement, differentNames: Boolean): TargetPresentation? {
    return when (element) {
      is PsiMethod -> PsiElementRenderingInfo.targetPresentation(element, PsiMethodRenderingInfo(differentNames))
      is PsiClass -> PsiElementRenderingInfo.targetPresentation(element, PsiClassRenderingInfo.INSTANCE)
      is PsiFunctionalExpression -> PsiElementRenderingInfo.targetPresentation(element, PsiFunctionalExpressionRenderingInfo.INSTANCE)
      else -> null
    }
  }
}
