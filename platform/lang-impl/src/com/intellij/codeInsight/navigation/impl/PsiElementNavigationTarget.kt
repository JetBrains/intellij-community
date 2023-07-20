// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation.impl

import com.intellij.codeInsight.navigation.targetPresentation
import com.intellij.model.Pointer
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.navigation.NavigationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.refactoring.suggested.createSmartPointer

internal class PsiElementNavigationTarget(private val myElement: PsiElement) : NavigationTarget {

  override fun createPointer(): Pointer<out NavigationTarget> = Pointer.delegatingPointer(
    myElement.createSmartPointer(), ::PsiElementNavigationTarget
  )

  override fun computePresentation(): TargetPresentation = targetPresentation(myElement)

  override fun navigationRequest(): NavigationRequest? = myElement.psiNavigatable()?.navigationRequest()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as PsiElementNavigationTarget

    return myElement == other.myElement
  }

  override fun hashCode(): Int {
    return myElement.hashCode()
  }
}
