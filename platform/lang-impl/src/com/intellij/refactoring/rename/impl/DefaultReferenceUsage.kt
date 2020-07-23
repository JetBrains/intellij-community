// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.impl

import com.intellij.model.Pointer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiFileRange
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.rename.api.PsiRenameUsage
import com.intellij.refactoring.rename.api.RenameConflict
import com.intellij.refactoring.rename.api.RenameUsage

/**
 * If [RenameUsage] is not defined for a reference,
 * then the platform treats the reference as a non-renameable [RenameUsage]
 * by creating an instance of this class.
 */
internal class DefaultReferenceUsage(
  override val file: PsiFile,
  override val range: TextRange
) : PsiRenameUsage {

  override val declaration: Boolean get() = false

  override fun conflicts(newName: String): List<RenameConflict> {
    return listOf(RenameConflict.fromText(RefactoringBundle.message("rename.usage.unmodifiable")))
  }

  override fun createPointer(): Pointer<out DefaultReferenceUsage> = DefaultReferenceUsagePointer(file, range)

  private class DefaultReferenceUsagePointer(file: PsiFile, range: TextRange) : Pointer<DefaultReferenceUsage> {

    private val rangePointer: SmartPsiFileRange = SmartPointerManager.getInstance(file.project).createSmartPsiFileRangePointer(file, range)

    override fun dereference(): DefaultReferenceUsage? {
      val file: PsiFile = rangePointer.element ?: return null
      val range: TextRange = rangePointer.range?.let(TextRange::create) ?: return null
      return DefaultReferenceUsage(file, range)
    }
  }
}
