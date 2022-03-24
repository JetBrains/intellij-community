// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation.impl

import com.intellij.codeInsight.navigation.*
import com.intellij.navigation.DirectNavigationProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.elementsAroundOffsetUp

internal fun fromDirectNavigation(file: PsiFile, offset: Int): GTDActionData? {
  for ((element, _) in file.elementsAroundOffsetUp(offset)) {
    for (provider in DirectNavigationProvider.EP_NAME.extensions) {
      val navigationElement = provider.getNavigationElement(element)
      if (navigationElement != null) {
        return DirectNavigationData(element, navigationElement, provider)
      }
    }
  }
  return null
}

private class DirectNavigationData(
  private val sourceElement: PsiElement,
  private val targetElement: PsiElement,
  private val navigationProvider: DirectNavigationProvider
) : GTDActionData {

  override fun ctrlMouseInfo(): CtrlMouseInfo = object : BaseCtrlMouseInfo(listOf(sourceElement.textRange)) {
    override fun getDocInfo(): CtrlMouseDocInfo = CtrlMouseDocInfo.EMPTY
    override fun isValid(): Boolean = true
  }

  override fun ctrlMouseData(): CtrlMouseData = rangeOnlyCtrlMouseData(listOf(sourceElement.textRange))

  override fun result(): NavigationActionResult? {
    val request = targetElement.psiNavigatable()?.navigationRequest() ?: return null
    return NavigationActionResult.SingleTarget(request, navigationProvider)
  }
}
