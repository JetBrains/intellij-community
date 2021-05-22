// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.impl

import com.intellij.find.usages.api.PsiUsage
import com.intellij.model.Pointer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.api.ModifiableRenameUsage
import com.intellij.refactoring.rename.api.ModifiableRenameUsage.FileUpdater
import com.intellij.refactoring.rename.api.PsiRenameUsage

internal class TextRenameUsage(
  private val psiUsage: PsiUsage,
  override val fileUpdater: FileUpdater
) : PsiRenameUsage, ModifiableRenameUsage {

  override val declaration: Boolean get() = psiUsage.declaration

  override val file: PsiFile get() = psiUsage.file

  override val range: TextRange get() = psiUsage.range

  override fun createPointer(): Pointer<out TextRenameUsage> = TextUsagePointer(psiUsage, fileUpdater)

  private class TextUsagePointer(psiUsage: PsiUsage, private val fileUpdater: FileUpdater) : Pointer<TextRenameUsage> {

    private val myTextUsagePointer: Pointer<out PsiUsage> = psiUsage.createPointer()

    override fun dereference(): TextRenameUsage? {
      return myTextUsagePointer.dereference()?.let {
        TextRenameUsage(it, fileUpdater)
      }
    }
  }
}
