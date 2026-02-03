// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.logical

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.impl.java.JavaClassTreeElement
import com.intellij.ide.structureView.logical.LogicalStructureTreeElementProvider
import com.intellij.psi.PsiClass

class JavaPsiClassLogicalStructureTreeElementProvider: LogicalStructureTreeElementProvider<PsiClass> {

  override fun getModelClass(): Class<PsiClass> = PsiClass::class.java

  override fun getTreeElement(model: PsiClass): StructureViewTreeElement {
    return JavaClassTreeElement(model, false)
  }

}