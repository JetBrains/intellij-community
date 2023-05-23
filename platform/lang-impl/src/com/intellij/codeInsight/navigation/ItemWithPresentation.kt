// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation

import com.intellij.model.Pointer
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement

class ItemWithPresentation(val item: Any, var presentation: TargetPresentation) : Pointer<PsiElement?> {
  override fun dereference(): PsiElement? {
    return if (item is Pointer<*>) item.dereference() as PsiElement? else null
  }
}
