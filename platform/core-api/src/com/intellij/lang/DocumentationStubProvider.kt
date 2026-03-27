// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang

import com.intellij.lang.DocumentationStubProvider.Companion.EP_NAME
import com.intellij.openapi.editor.Document
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus.OverrideOnly

/**
 * Extension point to provide documentation stub for a [PsiElement].
 */
@OverrideOnly
interface DocumentationStubProvider {

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<DocumentationStubProvider> =
      ExtensionPointName.create("com.intellij.lang.documentationStubProvider")
  }

  /**
   * @return documentation stub to be inserted for the given [element], or `null` if this provider cannot provide a stub
   */
  fun documentationStub(element: PsiElement): String?

  /**
   * Inserts a documentation stub directly into the [document] at the given [offset] for the given [element].
   * Use this when the stub generation requires direct document manipulation.
   *
   * @return `true` if the stub was inserted, `false` if this provider cannot handle the element
   *         or prefers to use `documentationStub` instead.
   */
  fun insertDocumentationStub(element: PsiElement, document: Document, offset: Int): Boolean = false

  /**
   * @return the [PsiComment] documentation for the [element] if it exists
   */
  fun findDocComment(element: PsiElement): PsiComment?

  /**
   * @return the documented element, or `null` if this provider cannot handle the element
   */
  fun findDocumentedElement(element: PsiElement): PsiElement? = null
}

/**
 * Iterates over [DocumentationStubProvider]s and tries to insert documentation stub.
 *
 * For each provider, [DocumentationStubProvider.insertDocumentationStub] is tried first; if it returns `false`,
 * [DocumentationStubProvider.documentationStub] is called and the returned string is inserted into the document.
 *
 * @return `true` if a stub was inserted, `false` if no provider handled the element
 */
fun insertStub(anchor: PsiElement, document: Document, offset: Int): Boolean {
  val inserted = EP_NAME.computeSafeIfAny { provider ->
    if (provider.insertDocumentationStub(anchor, document, offset)) true
    else {
      val stub = provider.documentationStub(anchor) ?: return@computeSafeIfAny null
      document.insertString(offset, stub)
      true
    }
  }
  return inserted ?: false
}

/**
 * Finds an existing doc comment for [anchor] by iterating registered [DocumentationStubProvider]s.
 *
 * @return the first non-null [PsiComment] returned by any provider, or `null` if none handles the element
 */
fun findDocComment(anchor: PsiElement): PsiComment? =
  EP_NAME.computeSafeIfAny { it.findDocComment(anchor) }

/**
 * Finds the nearest documented element for [element] by walking up the PSI tree,
 * by iterating registered [DocumentationStubProvider]s.
 *
 * @return the first non-null [PsiElement] returned by any provider, or `null` if none handles the element
 */
fun findDocumentedElement(element: PsiElement): PsiElement? =
  EP_NAME.computeSafeIfAny { it.findDocumentedElement(element) }