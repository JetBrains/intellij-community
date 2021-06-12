// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.impl

import com.intellij.find.usages.api.PsiUsage
import com.intellij.model.Pointer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.api.ModifiableRenameUsage
import com.intellij.refactoring.rename.api.ModifiableRenameUsage.FileUpdater
import com.intellij.refactoring.rename.api.PsiRenameUsage
import com.intellij.refactoring.rename.api.ReplaceTextTargetContext

internal class TextRenameUsage(
  private val psiUsage: PsiUsage,
  override val fileUpdater: FileUpdater,
  val context: ReplaceTextTargetContext,
) : PsiRenameUsage, ModifiableRenameUsage {

  override val declaration: Boolean get() = psiUsage.declaration

  override val file: PsiFile get() = psiUsage.file

  override val range: TextRange get() = psiUsage.range

  override fun createPointer(): Pointer<out TextRenameUsage> {
    val fileUpdater = fileUpdater // don't capture `this`
    val context = context
    return Pointer.delegatingPointer(psiUsage.createPointer(), listOf(TextRenameUsage::class.java, fileUpdater, context)) {
      TextRenameUsage(it, fileUpdater, context)
    }
  }
}
