// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java

import com.intellij.lang.DocumentationStubProvider
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaDocumentedElement
import com.intellij.psi.PsiTypeParameter

internal class JavadocStubProvider : DocumentationStubProvider {

  override fun documentationStub(element: PsiElement): String? {
    val docComment = (element as? PsiJavaDocumentedElement)?.docComment ?: return null
    return JavaDocumentationProvider().generateDocumentationContentStub(docComment)
  }

  override fun findDocComment(element: PsiElement): PsiComment? =
    (element as? PsiJavaDocumentedElement)?.docComment

  override fun findDocumentedElement(element: PsiElement): PsiElement? {
    var current: PsiElement? = element
    while (current != null) {
      if (current is PsiJavaDocumentedElement &&
          current !is PsiTypeParameter &&
          current !is PsiAnonymousClass) {
        return current
      }
      current = current.parent
    }
    return null
  }

}
