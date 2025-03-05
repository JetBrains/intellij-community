// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.logical

import com.intellij.ide.structureView.logical.LogicalStructureElementsProvider
import com.intellij.psi.PsiClass

abstract class PsiClassLogicalElementProvider<T> : LogicalStructureElementsProvider<PsiClass, T> {

  abstract fun convert(p: PsiClass): T?

  override fun getElements(parent: PsiClass): List<T> {
    val t = convert(parent)
    return if (t != null) listOf(t) else emptyList()
  }

}