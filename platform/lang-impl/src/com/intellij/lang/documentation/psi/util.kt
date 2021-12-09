// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.psi

import com.intellij.lang.documentation.DocumentationTarget
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement

@JvmField
internal val LOG: Logger = Logger.getInstance("#com.intellij.lang.documentation.psi")

internal fun psiDocumentationTarget(element: PsiElement): DocumentationTarget? {
  for (factory in PsiDocumentationTargetFactory.EP_NAME.extensionList) {
    return factory.documentationTarget(element)
           ?: continue
  }
  return null
}
