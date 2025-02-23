// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("StubElementUtil")
@file:ApiStatus.Experimental

package com.intellij.psi.stubs

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

/**
 * A shortcut for checking if a given [psi] can have a stub element.
 * @see StubElementFactory.shouldCreateStub
 */
fun StubElementFactory<*, *>.shouldCreateStubForPsi(psi: PsiElement): Boolean {
  val node = psi.node ?: return false
  return shouldCreateStub(node)
}