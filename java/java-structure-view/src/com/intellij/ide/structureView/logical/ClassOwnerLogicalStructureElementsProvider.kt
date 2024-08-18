// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.logical

import com.intellij.ide.logical.LogicalStructureElementsProvider
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner

class ClassOwnerLogicalStructureElementsProvider: LogicalStructureElementsProvider<PsiClassOwner, PsiClass> {
  override fun getElements(parent: PsiClassOwner): List<PsiClass> {
    return parent.classes.toList()
  }
  override fun forLogicalModelClass(): Class<PsiClassOwner> = PsiClassOwner::class.java
}
