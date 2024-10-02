// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation

import com.intellij.model.Pointer
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import org.jetbrains.annotations.ApiStatus
import java.util.function.Function

class ItemWithPresentation(val item: Any, var presentation: TargetPresentation) : Pointer<PsiElement> {

  override fun dereference(): PsiElement? {
    return if (item is Pointer<*>) item.dereference() as PsiElement? else null
  }

  override fun toString(): String {
    return presentation.presentableText
  }
}

@ApiStatus.Internal
fun <T: PsiElement> createItem(psiElement: T, presentationProvider: Function<T, TargetPresentation>): ItemWithPresentation {
  return ItemWithPresentation(SmartPointerManager.createPointer(psiElement), presentationProvider.apply(psiElement))
}