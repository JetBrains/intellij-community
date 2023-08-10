// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete.api

import com.intellij.find.usages.api.PsiUsage
import com.intellij.model.Pointer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.safeDelete.impl.DefaultPsiSafeDeleteUsage

interface PsiSafeDeleteUsage : SafeDeleteUsage {
  override fun createPointer(): Pointer<out PsiSafeDeleteUsage>

  /**
   * File with the usage.
   */
  val file: PsiFile

  /**
   * Range, relative to the [file].
   */
  val range: TextRange

  companion object {
    @JvmStatic
    fun defaultPsiSafeDeleteUsage(psiUsage: PsiUsage, isSafeToDelete : Boolean): PsiSafeDeleteUsage {
      return DefaultPsiSafeDeleteUsage(psiUsage, isSafeToDelete, null)
    }

    @JvmStatic
    fun defaultPsiSafeDeleteUsage(element: PsiElement, isSafeToDelete : Boolean): PsiSafeDeleteUsage {
      return defaultPsiSafeDeleteUsage(PsiUsage.textUsage(element.containingFile, element.textRange), isSafeToDelete)
    }
  }
}

