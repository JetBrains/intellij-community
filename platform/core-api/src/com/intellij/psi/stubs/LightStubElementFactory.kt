// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs

import com.intellij.lang.LighterAST
import com.intellij.lang.LighterASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

/**
 * Factory for creating [Stub] by [Psi] and vice versa for a given [com.intellij.psi.tree.IElementType].
 *
 * Register your factory in your implementation of [com.intellij.psi.stubs.StubRegistryExtension]
 *
 * @see [LightLanguageStubDefinition]
 */
@ApiStatus.Experimental
interface LightStubElementFactory<Stub : StubElement<*>, Psi : PsiElement> : StubElementFactory<Stub, Psi> {

  /**
   * Factory method producing a [Stub] for the given [node] in a light [tree].
   * Use [parentStub] as the produced stub's parent.
   *
   * This method is called only if [shouldCreateStub] returns `true` for [node].
   *
   * @return new stub instance corresponding to [psi]
   */
  fun createStub(
    tree: LighterAST,
    node: LighterASTNode,
    parentStub: StubElement<*>,
  ): Stub

  /**
   * Checks if a given [node] in a given light [tree] can have a stub.
   *
   * E.g., there can be a local variable node that should not have a stub,
   *       and there can be a global variable node that should have a stub.
   *
   * @return true if the given [node] can have a stub.
   */
  fun shouldCreateStub(
    tree: LighterAST,
    node: LighterASTNode,
    parentStub: StubElement<*>,
  ): Boolean = true
}