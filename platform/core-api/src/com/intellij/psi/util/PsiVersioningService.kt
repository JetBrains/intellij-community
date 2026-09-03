// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util

import com.intellij.lang.ASTNode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceOrNull
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PsiVersioningService {

  /**
   * An object representing a key to a PSI version.
   * This object can be used for executing computations that work with a specific PSI snapshot or for mutating PSI in its own isolated transaction.
   *
   * The exact implementation is deliberately hidden.
   */
  interface OpaquePsiVersion

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
     * If this function is invoked with [read access allowed][com.intellij.openapi.application.Application.isReadAccessAllowed],
     * then [action] runs directly with a read action.
     *
     * The PSI tree inside [action] will be _consistent_, but not up to date.
     * In other words, parallel write actions can modify the PSI structure, but these modifications will not be visible to [action].
     * Notably, there might be no read access inside [action], so access to resolve and workspace model may fail.
     */
    @JvmStatic
    fun <T> freezePsiVersion(action: () -> T): T = getInstance().runAndFreezePsiVersion(action)

    @ApiStatus.Internal
    fun isInsideVersioningButNotLocks(): Boolean = getInstance().isInsideVersioningButNotLocks()

    /**
     * Captures a descriptor of the current versioned snapshot.
     *
     * This function is useful for performing isolated incremental modifications of versioned structures,
     * such as PSI modifications on top of local patches over document.
     *
     * We can graphically represent the effect of this function as the following:
     * ```kotlin
     * forked version:         ------ A*         ------ C*
     *                        /                 /
     * main versions:   >--- A ------ B ------ C ------ D -->
     * ```
     * Here, `forkTimeline` invoked with published version `A` would result in `A*`.
     *
     * The returned object would represent the key to `A*` and allow to perform operations inside via [executeWithTimeline].
     *
     * The results will be discarded when [forgetForkedTimeline] is executed. Please design your use-cases carefully to avoid memory leaks.
     */
    @JvmStatic
    fun forkTimeline(): OpaquePsiVersion = getInstance().doForkTimeline()

    /**
     * Releases the resources associated with [opaqueVersion].
     */
    @JvmStatic
    fun forgetForkedTimeline(opaqueVersion: OpaquePsiVersion): Unit = getInstance().doForgetForkedTimeline(opaqueVersion)

    /**
     * Returns the key of the version that the current computation uses.
     * If the current computation runs not in [executeWithTimeline], then this function returns `null`
     *
     * Use the result only to compare it with another key, for example in an assertion.
     */
    @JvmStatic
    fun currentOpaqueVersion(): OpaquePsiVersion? = getInstance().doGetCurrentOpaqueVersion()

    /**
     * Runs [action] where each access to versioned structures interacts with [opaqueVersion].
     *
     * We can graphically represent the effect of this function as the following:
     * ```kotlin
     * executeWithTimeline:       ------ A*         ------ C*
     *                           /                 /
     * normal execution:   >--- A ------ B ------ C ------ D -->
     * ```
     * Here, [forkTimeline] executed at version `A` will allow working in `A*` via [executeWithTimeline].
     *
     * All changes to versioned structures (e.g. PSI) made inside [executeWithTimeline] are invisible to other modifications to this structure.
     * The changes are effectively discarded after [forgetForkedTimeline] is invoked.
     *
     * It is possible to perform a [lightweight document commit][com.intellij.psi.PsiDocumentManager.commitDocument] inside this block.
     */
    @JvmStatic
    fun <T> executeWithTimeline(opaqueVersion: OpaquePsiVersion, action: () -> T): T = getInstance().doExecuteWithTimeline(opaqueVersion, action)


    private fun getInstance(): PsiVersioningService {
      return ApplicationManager.getApplication().serviceOrNull<PsiVersioningService>() ?: Fallback
    }

    private object Fallback: PsiVersioningService
  }

  @ApiStatus.Internal
  fun <T> runInVersionedEnvironment(node: ASTNode, action: () -> T): T = action()

  @ApiStatus.Internal
  fun <T> runAndFreezePsiVersion(action: () -> T): T = action()

  private object IgnoredOpaquePsiVersion: OpaquePsiVersion

  @ApiStatus.Internal
  fun doForkTimeline(): OpaquePsiVersion = IgnoredOpaquePsiVersion

  @ApiStatus.Internal
  fun doForgetForkedTimeline(opaqueVersion: OpaquePsiVersion): Unit = Unit

  @ApiStatus.Internal
  fun doGetCurrentOpaqueVersion(): OpaquePsiVersion? = IgnoredOpaquePsiVersion

  @ApiStatus.Internal
  fun <T> doExecuteWithTimeline(opaqueVersion: OpaquePsiVersion, action: () -> T): T = action()

  @ApiStatus.Internal
  fun getCurrentVersion(): Long = -1

  @ApiStatus.Internal
  fun getNextSibling(element: PsiElement, version: Long): PsiElement? = element.nextSibling

  @ApiStatus.Internal
  fun getPrevSibling(element: PsiElement, version: Long): PsiElement? = element.prevSibling

  @ApiStatus.Internal
  fun getParent(element: PsiElement, version: Long): PsiElement? = element.parent

  @ApiStatus.Internal
  fun getFirstChild(element: PsiElement, version: Long): PsiElement? = element.firstChild

  @ApiStatus.Internal
  fun isInsideVersioningButNotLocks(): Boolean = false
}
