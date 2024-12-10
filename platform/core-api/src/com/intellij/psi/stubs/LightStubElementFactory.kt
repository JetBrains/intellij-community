// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs

import com.intellij.lang.LighterAST
import com.intellij.lang.LighterASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

/**
 * Register your factory in your implementation of [com.intellij.psi.stubs.StubRegistryExtension]
 *
 * @see [LightLanguageStubDefinition]
 */
@ApiStatus.Experimental
interface LightStubElementFactory<Stub : StubElement<*>, Psi : PsiElement> : StubElementFactory<Stub, Psi> {
  fun createStub(tree: LighterAST, node: LighterASTNode, parentStub: StubElement<*>): Stub

  fun shouldCreateStub(tree: LighterAST, node: LighterASTNode, parentStub: StubElement<*>): Boolean = true
}