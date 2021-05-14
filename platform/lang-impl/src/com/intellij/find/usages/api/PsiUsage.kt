// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.usages.api

import com.intellij.find.usages.impl.TextUsage
import com.intellij.model.Pointer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

interface PsiUsage : Usage {

  override fun createPointer(): Pointer<out PsiUsage>

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
    fun textUsage(file: PsiFile, range: TextRange): PsiUsage = TextUsage(file, range)

    @JvmStatic
    fun textUsage(element: PsiElement, rangeInElement: TextRange): PsiUsage {
      if (element is PsiFile) {
        return textUsage(element, rangeInElement)
      }
      else {
        return textUsage(element.containingFile, rangeInElement.shiftRight(element.textRange.startOffset))
      }
    }
  }
}
