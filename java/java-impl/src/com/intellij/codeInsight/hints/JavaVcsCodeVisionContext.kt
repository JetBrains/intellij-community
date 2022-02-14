// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod

class JavaVcsCodeVisionContext : VcsCodeVisionLanguageContext {
  override fun isAccepted(element: PsiElement): Boolean {
    return element is PsiMethod || element is PsiClass
  }
}