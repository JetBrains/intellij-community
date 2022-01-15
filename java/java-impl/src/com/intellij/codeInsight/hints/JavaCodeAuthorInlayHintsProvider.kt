// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.daemon.impl.JavaCodeVisionUsageCollector.*
import com.intellij.psi.*

internal class JavaCodeAuthorInlayHintsProvider : VcsCodeAuthorInlayHintsProvider() {

  override fun isAccepted(element: PsiElement): Boolean =
    when (element) {
      is PsiClass -> isAcceptedClass(element)
      is PsiMethod -> isAcceptedClass(element.containingClass)
      else -> false
    }

  private fun isAcceptedClass(element: PsiClass?) =
    element != null && element !is PsiAnonymousClass && element !is PsiTypeParameter

  override fun getClickHandler(element: PsiElement): () -> Unit {
    val project = element.project
    val location = if (element is PsiClass) CLASS_LOCATION else METHOD_LOCATION

    return { logCodeAuthorClicked(project, location) }
  }
}