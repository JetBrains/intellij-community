// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation.impl

import com.intellij.codeInsight.navigation.targetPresentation
import com.intellij.model.Pointer
import com.intellij.navigation.NavigationRequest
import com.intellij.navigation.NavigationTarget
import com.intellij.navigation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.refactoring.suggested.createSmartPointer

internal class PsiElementNavigationTarget(private val myElement: PsiElement) : NavigationTarget {

  override fun createPointer(): Pointer<out NavigationTarget> = Pointer.delegatingPointer(
    myElement.createSmartPointer(), PsiElementNavigationTarget::class.java, ::PsiElementNavigationTarget
  )

  override fun getTargetPresentation(): TargetPresentation = targetPresentation(myElement)

  override fun navigationRequest(): NavigationRequest? = myElement.psiNavigatable()?.navigationRequest()

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
