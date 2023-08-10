// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.psi

import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.psi.PsiElement

@JvmField
internal val LOG: Logger = Logger.getInstance("#com.intellij.lang.documentation.psi")

fun psiDocumentationTarget(element: PsiElement, originalElement: PsiElement?): DocumentationTarget {
  for (ext in PsiDocumentationTargetProvider.EP_NAME.extensionList) {
    return ext.documentationTarget(element, originalElement)
           ?: continue
  }
  return PsiElementDocumentationTarget(element.project, element, originalElement)
}

internal fun isNavigatableQuickDoc(source: PsiElement?, target: PsiElement): Boolean {
  return target !== source && target !== source?.parent
}
