// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete.impl

import com.intellij.find.usages.api.PsiUsage
import com.intellij.model.Pointer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.api.FileOperation
import com.intellij.refactoring.safeDelete.api.FileUpdater
import com.intellij.refactoring.safeDelete.api.PsiSafeDeleteUsage
import com.intellij.util.text.StringOperation

open class DefaultPsiSafeDeleteUsage(private val psiUsage: PsiUsage,
                                     override var isSafeToDelete: Boolean,
                                     override var conflictMessage: String?
) : PsiSafeDeleteUsage {
  override fun createPointer(): Pointer<out PsiSafeDeleteUsage> {
    return Pointer.delegatingPointer(
      psiUsage.createPointer()
    ) { DefaultPsiSafeDeleteUsage(it, isSafeToDelete, conflictMessage) }
  }

  override val file: PsiFile
    get() = psiUsage.file
  override val range: TextRange
    get() = psiUsage.range

  override val fileUpdater: FileUpdater
    get() = object : FileUpdater {
      override fun prepareFileUpdate(): Collection<FileOperation> {
        return listOf(FileOperation.modifyFile(file, StringOperation.remove(range)))
      }
    }
}
