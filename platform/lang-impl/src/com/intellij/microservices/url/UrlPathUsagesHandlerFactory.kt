// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.url

import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.microservices.url.references.UrlSegmentReferenceTarget
import com.intellij.psi.PsiElement

internal class UrlPathUsagesHandlerFactory : FindUsagesHandlerFactory() {
  override fun canFindUsages(element: PsiElement): Boolean = element is UrlSegmentReferenceTarget

  override fun createFindUsagesHandler(element: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler =
    object : FindUsagesHandler(element) {
      override fun getPrimaryElements(): Array<PsiElement> = arrayOf(myPsiElement)
    }
}