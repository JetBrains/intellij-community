// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
    ::DefaultPsiRenameUsage
  )

  override val declaration: Boolean get() = psiUsage.declaration

  override val file: PsiFile get() = psiUsage.file

  override val range: TextRange get() = psiUsage.range
}
