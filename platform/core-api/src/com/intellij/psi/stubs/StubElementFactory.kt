// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

/**
 * Register your factory in your implementation of [com.intellij.psi.stubs.StubRegistryExtension]
 */
@ApiStatus.Experimental
interface StubElementFactory<Stub, Psi> where Psi : PsiElement, Stub : StubElement<*> {
  fun createStub(psi: Psi, parentStub: StubElement<out PsiElement>?): Stub

  fun createPsi(stub: Stub): Psi?

  fun shouldCreateStub(node: ASTNode?): Boolean = true
}