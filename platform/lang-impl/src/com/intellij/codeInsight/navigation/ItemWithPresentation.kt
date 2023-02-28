// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation

import com.intellij.model.Pointer
import com.intellij.navigation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager

class ItemWithPresentation(val item: Any, var presentation: TargetPresentation) : Pointer<PsiElement?> {

  constructor(element: PsiElement) : this(SmartPointerManager.createPointer(element), targetPresentation(element))

  override fun dereference(): PsiElement? {
    return if (item is Pointer<*>) item.dereference() as PsiElement? else null
  }
}
