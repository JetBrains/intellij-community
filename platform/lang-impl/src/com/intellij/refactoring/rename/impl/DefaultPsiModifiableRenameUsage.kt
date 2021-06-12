// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    DefaultPsiModifiableRenameUsage::class.java,
    ::DefaultPsiModifiableRenameUsage
  )
}
