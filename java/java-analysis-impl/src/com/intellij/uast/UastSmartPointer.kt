// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uast

import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.uast.UElement
import org.jetbrains.uast.toUElement

public class UastSmartPointer<T : UElement>(uElement: T, public val targetClass: Class<T>) {

  private val sourcePointer: SmartPsiElementPointer<PsiElement>? = uElement.sourcePsi?.let { psi ->
    SmartPointerManager.getInstance(psi.project).createSmartPsiElementPointer(psi)
  }

  public val element: T?
    get() = sourcePointer?.element?.toUElement(targetClass)
}

public inline fun <reified T : UElement> T.createUastSmartPointer(): UastSmartPointer<T> = UastSmartPointer(this, T::class.java)

