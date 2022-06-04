// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.psi

import com.intellij.lang.documentation.DocumentationTarget
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement

@JvmField
internal val LOG: Logger = Logger.getInstance("#com.intellij.lang.documentation.psi")

internal fun psiDocumentationTarget(element: PsiElement, originalElement: PsiElement?): DocumentationTarget {
  for (factory in PsiDocumentationTargetFactory.EP_NAME.extensionList) {
    return factory.documentationTarget(element, originalElement)
           ?: continue
  }
  return PsiElementDocumentationTarget(element.project, element, originalElement)
}

internal fun isNavigatableQuickDoc(source: PsiElement?, target: PsiElement): Boolean {
  return target !== source && target !== source?.parent
}
