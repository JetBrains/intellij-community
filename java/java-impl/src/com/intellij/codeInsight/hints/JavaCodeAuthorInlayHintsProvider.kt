// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod

internal class JavaCodeAuthorInlayHintsProvider : VcsCodeAuthorInlayHintsProvider() {

  override fun isAccepted(element: PsiElement): Boolean = element is PsiClass || element is PsiMethod
}