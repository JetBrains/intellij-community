// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiFileRange
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.function.BiFunction

@Internal
internal class FileRangePointer<T>(
  private val base: SmartPsiFileRange,
  private val restoration: BiFunction<in PsiFile, in TextRange, out T>,
) : Pointer<T> {

  override fun dereference(): T? {
    val file = base.element ?: return null
    val range = base.range ?: return null
    return restoration.apply(file, TextRange.create(range))
  }
}
