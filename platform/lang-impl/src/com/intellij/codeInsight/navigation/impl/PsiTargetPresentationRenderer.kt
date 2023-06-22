// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation.impl

import com.intellij.codeInsight.navigation.TargetPresentationProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.presentation.java.SymbolPresentationUtil
import org.jetbrains.annotations.Nls
import javax.swing.Icon

open class PsiTargetPresentationRenderer<T: PsiElement>: TargetPresentationProvider<T> {

  @Nls
  open fun getElementText(element: T): String = SymbolPresentationUtil.getSymbolPresentableText(element)

  @Nls
  open fun getContainerText(element: T): String? = SymbolPresentationUtil.getSymbolContainerText(element)

  protected open fun getIcon(element: T): Icon? = element.getIcon(0)

  override fun getPresentation(element: T): TargetPresentation {
    return TargetPresentation.builder(getElementText(element))
      .containerText(getContainerText(element))
      .icon(getIcon(element))
      .presentation()
  }
}