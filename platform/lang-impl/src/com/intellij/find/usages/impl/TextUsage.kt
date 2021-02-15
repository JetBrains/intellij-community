// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.usages.impl

import com.intellij.find.usages.api.PsiUsage
import com.intellij.model.Pointer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiFileRange

internal class TextUsage(
  override val file: PsiFile,
  override val range: TextRange
) : PsiUsage {

  override fun createPointer(): Pointer<out TextUsage> = TextUsagePointer(file, range)

  override val declaration: Boolean get() = false

  private class TextUsagePointer(file: PsiFile, range: TextRange) : Pointer<TextUsage> {

    private val rangePointer: SmartPsiFileRange = SmartPointerManager.getInstance(file.project).createSmartPsiFileRangePointer(file, range)

    override fun dereference(): TextUsage? {
      val file: PsiFile = rangePointer.element ?: return null
      val range: TextRange = rangePointer.range?.let(TextRange::create) ?: return null
      return TextUsage(file, range)
    }
  }
}
