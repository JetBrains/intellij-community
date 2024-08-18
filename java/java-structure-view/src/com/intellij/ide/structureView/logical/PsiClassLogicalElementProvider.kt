// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.structureView.logical

import com.intellij.ide.logical.ConvertElementsProvider
import com.intellij.psi.PsiClass

abstract class  PsiClassLogicalElementProvider<T> : ConvertElementsProvider<PsiClass, T>() {
  override fun forLogicalModelClass(): Class<PsiClass> {
    return PsiClass::class.java
  }
}