// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.api

import com.intellij.find.usages.api.PsiUsage
import com.intellij.model.Pointer
import com.intellij.refactoring.rename.impl.DefaultPsiModifiableRenameUsage

interface PsiModifiableRenameUsage : PsiRenameUsage, ModifiableRenameUsage {

  override fun createPointer(): Pointer<out PsiModifiableRenameUsage>

  override val fileUpdater: ModifiableRenameUsage.FileUpdater
    get() = idFileRangeUpdater()

  companion object {

    /**
     * @return a usage which is updated by [idFileRangeUpdater]
     */
    @JvmStatic
    fun defaultPsiModifiableRenameUsage(psiUsage: PsiUsage): PsiModifiableRenameUsage {
      return DefaultPsiModifiableRenameUsage(PsiRenameUsage.defaultPsiRenameUsage(psiUsage))
    }
  }
}
