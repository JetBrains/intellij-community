// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.api

import com.intellij.find.usages.api.PsiUsage
import com.intellij.model.Pointer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.impl.DefaultPsiRenameUsage
import org.jetbrains.annotations.ApiStatus

/**
 * Usage in [PsiFile] content.
 */
@ApiStatus.Experimental
interface PsiRenameUsage : RenameUsage {

  override fun createPointer(): Pointer<out PsiRenameUsage>

  /**
   * File with the usage.
   */
  val file: PsiFile

  /**
   * Range, relative to the [file].
   */
  val range: TextRange

  companion object {

    /**
     * @return a usage which delegates its properties to corresponding [PsiUsage] properties
     */
    @JvmStatic
    fun defaultPsiRenameUsage(psiUsage: PsiUsage): PsiRenameUsage {
      return DefaultPsiRenameUsage(psiUsage)
    }
  }
}
