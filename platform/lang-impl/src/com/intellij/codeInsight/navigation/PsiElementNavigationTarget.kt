// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation

import com.intellij.ide.util.EditSourceUtil
import com.intellij.navigation.NavigationTarget
import com.intellij.navigation.TargetPresentation
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement

class PsiElementNavigationTarget(private val myElement: PsiElement) : NavigationTarget {

  private val myNavigatable by lazy {
    if (myElement is Navigatable) myElement else EditSourceUtil.getDescriptor(myElement)
  }

  private val myPresentation by lazy {
    PsiElementTargetPresentation(myElement)
  }

  override fun isValid(): Boolean = myElement.isValid

  override fun getNavigatable(): Navigatable? = myNavigatable

  override fun getTargetPresentation(): TargetPresentation = myPresentation
}
