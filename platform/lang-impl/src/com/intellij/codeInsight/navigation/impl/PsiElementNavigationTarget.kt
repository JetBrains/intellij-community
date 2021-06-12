// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation.impl

import com.intellij.codeInsight.navigation.targetPresentation
import com.intellij.navigation.NavigationTarget
import com.intellij.navigation.TargetPresentation
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement

internal class PsiElementNavigationTarget(private val myElement: PsiElement) : NavigationTarget {

  override fun isValid(): Boolean = myElement.isValid

  override fun getNavigatable(): Navigatable = psiNavigatable(myElement)

  override fun getTargetPresentation(): TargetPresentation = targetPresentation(myElement)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as PsiElementNavigationTarget

    if (myElement != other.myElement) return false

    return true
  }

  override fun hashCode(): Int {
    return myElement.hashCode()
  }
}
