// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.impl

import com.intellij.find.usages.api.PsiUsage
import com.intellij.model.Pointer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.api.PsiRenameUsage

internal class DefaultPsiRenameUsage(
  private val psiUsage: PsiUsage
) : PsiRenameUsage {

  override fun createPointer(): Pointer<out DefaultPsiRenameUsage> = Pointer.delegatingPointer(
    psiUsage.createPointer(),
    DefaultPsiRenameUsage::class.java,
    ::DefaultPsiRenameUsage
  )

  override val declaration: Boolean get() = psiUsage.declaration

  override val file: PsiFile get() = psiUsage.file

  override val range: TextRange get() = psiUsage.range
}
