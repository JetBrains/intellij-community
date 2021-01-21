// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.impl

import com.intellij.find.usages.impl.TextUsage
import com.intellij.model.Pointer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.api.FileOperation
import com.intellij.refactoring.rename.api.ModifiableRenameUsage
import com.intellij.refactoring.rename.api.ModifiableRenameUsage.FileUpdater
import com.intellij.refactoring.rename.api.PsiRenameUsage
import com.intellij.util.text.StringOperation

internal typealias TextReplacement = (newName: String) -> String?

internal class TextRenameUsage(
  private val textUsage: TextUsage,
  private val textReplacement: TextReplacement
) : PsiRenameUsage, ModifiableRenameUsage {

  override val declaration: Boolean get() = false

  override val file: PsiFile get() = textUsage.file

  override val range: TextRange get() = textUsage.range

  override fun createPointer(): Pointer<out TextRenameUsage> = TextUsagePointer(textUsage, textReplacement)

  private class TextUsagePointer(textUsage: TextUsage, private val textReplacement: TextReplacement) : Pointer<TextRenameUsage> {

    private val myTextUsagePointer: Pointer<out TextUsage> = textUsage.createPointer()

    override fun dereference(): TextRenameUsage? {
      return myTextUsagePointer.dereference()?.let {
        TextRenameUsage(it, textReplacement)
      }
    }
  }

  override val fileUpdater: FileUpdater get() = TextUsageUpdater

  private object TextUsageUpdater : FileUpdater {

    override fun prepareFileUpdate(usage: ModifiableRenameUsage, newName: String): Collection<FileOperation> {
      usage as TextRenameUsage
      val newText: String = usage.textReplacement(newName) ?: return emptyList()
      return listOf(FileOperation.modifyFile(
        usage.file,
        StringOperation.replace(usage.range, newText)
      ))
    }
  }
}
