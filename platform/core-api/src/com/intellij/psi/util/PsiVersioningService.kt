// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util

import com.intellij.lang.ASTNode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceOrNull
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PsiVersioningService {
  companion object {

    /**
     * Allows marking PSI elements that are created inside [action] as versioned according to the versioning state of [contextNode].
     *
     * A typical use-case for this function is the following:
     * ```kotlin
     * fun addWhitespace(element: PsiElement) {
     *   val functionElement: PsiElement = PsiVersioningService.createVersionedPsiElements(element.node) {
     *     SomePsiFactory.createSyntheticFunction()
     *   }
     *   functionElement.add(element)
     * }
     * ```
     *
     * Without [createVersionedPsiElements], we could have a violation of versioned PSI invariants:
     * the created `functionElement` could be non-versioned, and `element` could be versioned,
     * where the Platform disallows adding versioned elements to non-versioned trees.
     *
     * With [createVersionedPsiElements], the created `whitespaceElement` will be compatible with [contextNode], and it can be safely attached to `element`.
     */
    @JvmStatic
    fun <T> createVersionedPsiElements(contextNode: ASTNode, action: () -> T): T = getInstance().runInVersionedEnvironment(contextNode, action)

    /**
     * Overload of [createVersionedPsiElements] with [PsiElement] instead of [ASTNode]
     */
    @JvmStatic
    fun <T> createVersionedPsiElements(contextElement: PsiElement, action: () -> T): T {
      val node = contextElement.node
      if (node == null) {
        return action()
      }
      return getInstance().runInVersionedEnvironment(node, action)
    }

    /**
     * Executes [action] with allowing access to PSI Syntax Tree (the [ASTNode] hierarchy).
     * It is possible to work with PSI elements (the [PsiElement] hierarchy),
     * as long as the use-cases concern only the syntax, and not semantics (i.e., references and resolve).
     *
     * The PSI tree inside [action] will be _consistent_, but not up to date.
     * In other words, parallel write actions can modify the PSI structure, but these modifications will not be visible to [action].
     * Notably, there might be no read access inside [action], so access to resolve and workspace model may fail.
     */
    @JvmStatic
    fun <T> freezePsiVersion(action: () -> T): T = getInstance().runAndFreezePsiVersion(action)

    private fun getInstance(): PsiVersioningService {
      return ApplicationManager.getApplication().serviceOrNull<PsiVersioningService>() ?: Fallback
    }

    private object Fallback: PsiVersioningService
  }

  @ApiStatus.Internal
  fun <T> runInVersionedEnvironment(node: ASTNode, action: () -> T): T = action()

  @ApiStatus.Internal
  fun <T> runAndFreezePsiVersion(action: () -> T): T = action()
}