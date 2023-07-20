// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.usages.impl

import com.intellij.find.usages.api.PsiUsage
import com.intellij.model.Pointer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

internal class TextUsage(
  override val file: PsiFile,
  override val range: TextRange
) : PsiUsage {

  override fun createPointer(): Pointer<out TextUsage> {
    return Pointer.fileRangePointer(file, range, ::TextUsage)
  }

  override val declaration: Boolean get() = false
}
