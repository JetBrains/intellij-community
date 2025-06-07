// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

/**
 * Factory for creating [Stub] by [Psi] and vice versa for a given [com.intellij.psi.tree.IElementType].
 *
 * Register your factory in your implementation of [com.intellij.psi.stubs.StubRegistryExtension]
 */
@ApiStatus.Experimental
interface StubElementFactory<Stub, Psi> where Psi : PsiElement, Stub : StubElement<*> {

  /**
   * Factory method producing [Stub] for the given [psi].
   * Use [parentStub] as the produced stub's parent. it's `null` if the resulting stub is the root of the stub tree.
   *
   * This method is called only if [shouldCreateStub] returns `true` for [psi].
   *
   * @return new stub instance corresponding to [psi]
   */
  fun createStub(psi: Psi, parentStub: StubElement<out PsiElement>?): Stub

  /**
   * factory method producing [Psi] for the corresponding [stub]
   *
   * @return new psi instance corresponding to [stub].
   */
  fun createPsi(stub: Stub): Psi?

  /**
   * Checks if [node] can have a stub.
   *
   * E.g., there can be a local variable node that should not have a stub,
   *       and there can be a global variable node that should have a stub.
   *
   * @return true if the given [node] can have a stub.
   */
  fun shouldCreateStub(node: ASTNode): Boolean = true
}