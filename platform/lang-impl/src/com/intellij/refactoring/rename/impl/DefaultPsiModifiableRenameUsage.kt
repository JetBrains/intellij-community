// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.impl

import com.intellij.model.Pointer
import com.intellij.refactoring.rename.api.PsiModifiableRenameUsage
import com.intellij.refactoring.rename.api.PsiRenameUsage

internal class DefaultPsiModifiableRenameUsage(
  private val psiRenameUsage: PsiRenameUsage
) : PsiRenameUsage by psiRenameUsage,
    PsiModifiableRenameUsage {

  override fun createPointer(): Pointer<out DefaultPsiModifiableRenameUsage> = Pointer.delegatingPointer(
    psiRenameUsage.createPointer(),
    ::DefaultPsiModifiableRenameUsage
  )
}
