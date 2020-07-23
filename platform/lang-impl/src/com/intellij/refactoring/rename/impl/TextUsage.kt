// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.impl

import com.intellij.model.Pointer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiFileRange
import com.intellij.refactoring.rename.api.FileOperation
import com.intellij.refactoring.rename.api.ModifiableRenameUsage
import com.intellij.refactoring.rename.api.ModifiableRenameUsage.FileUpdater
import com.intellij.refactoring.rename.api.PsiRenameUsage
import com.intellij.refactoring.rename.api.RenameUsage
import com.intellij.util.text.StringOperation

internal typealias TextReplacement = (newName: String) -> String?

internal class TextUsage(
  override val file: PsiFile,
  override val range: TextRange,
  private val textReplacement: TextReplacement
) : PsiRenameUsage, ModifiableRenameUsage {

  override val declaration: Boolean get() = false

  override fun createPointer(): Pointer<out TextUsage> = TextUsagePointer(file, range, textReplacement)

  private class TextUsagePointer(file: PsiFile, range: TextRange, private val textReplacement: TextReplacement) : Pointer<TextUsage> {

    private val rangePointer: SmartPsiFileRange = SmartPointerManager.getInstance(file.project).createSmartPsiFileRangePointer(file, range)

    override fun dereference(): TextUsage? {
      val file: PsiFile = rangePointer.element ?: return null
      val range: TextRange = rangePointer.range?.let(TextRange::create) ?: return null
      return TextUsage(file, range, textReplacement)
    }
  }

  override val fileUpdater: FileUpdater? get() = TextUsageUpdater

  private object TextUsageUpdater : FileUpdater {

    override fun prepareFileUpdate(usage: ModifiableRenameUsage, newName: String): Collection<FileOperation> {
      usage as TextUsage
      val newText: String = usage.textReplacement(newName) ?: return emptyList()
      return listOf(FileOperation.modifyFile(
        usage.file,
        StringOperation.replace(usage.range, newText)
      ))
    }
  }

  companion object {

    fun createTextUsage(element: PsiElement, rangeInElement: TextRange, textReplacement: TextReplacement): RenameUsage {
      if (element is PsiFile) {
        return createTextUsage(element, rangeInElement, textReplacement)
      }
      else {
        return createTextUsage(element.containingFile, rangeInElement.shiftRight(element.textRange.startOffset), textReplacement)
      }
    }

    fun createTextUsage(file: PsiFile, rangeInFile: TextRange, textReplacement: TextReplacement): RenameUsage {
      return TextUsage(file, rangeInFile, textReplacement)
    }
  }
}
