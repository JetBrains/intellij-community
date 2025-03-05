// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.impl

import com.intellij.find.usages.api.PsiUsage
import com.intellij.model.Pointer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.api.ModifiableRenameUsage
import com.intellij.refactoring.rename.api.ModifiableRenameUsage.FileUpdater
import com.intellij.refactoring.rename.api.PsiRenameUsage
import com.intellij.refactoring.rename.api.ReplaceTextTargetContext
import com.intellij.usages.impl.rules.UsageType

internal class TextRenameUsage(
  private val psiUsage: PsiUsage,
  override val fileUpdater: FileUpdater,
  val context: ReplaceTextTargetContext,
) : PsiRenameUsage, ModifiableRenameUsage {

  override val declaration: Boolean get() = psiUsage.declaration

  override val file: PsiFile get() = psiUsage.file

  override val range: TextRange get() = psiUsage.range

  override val usageType: UsageType? get() = psiUsage.usageType

  override fun createPointer(): Pointer<out TextRenameUsage> {
    val fileUpdater = fileUpdater // don't capture `this`
    val context = context
    return Pointer.delegatingPointer(psiUsage.createPointer()) {
      TextRenameUsage(it, fileUpdater, context)
    }
  }
}
