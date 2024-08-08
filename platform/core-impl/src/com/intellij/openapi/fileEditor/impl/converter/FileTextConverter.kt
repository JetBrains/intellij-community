// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.converter

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface FileTextConverter {
  fun isApplicable(virtualFile: VirtualFile): Boolean
  fun convertToSaveTextToFile(text: String, virtualFile: VirtualFile): String
  fun convertToLoadDocumentFromFile(text: CharSequence, virtualFile: VirtualFile): CharSequence

  companion object {
    val EP: ExtensionPointName<FileTextConverter> = ExtensionPointName("com.intellij.fileEditor.fileTextConverter")

    @JvmStatic
    fun convertToSaveDocumentTextToFile(text: String, virtualFile: VirtualFile): String {
      val textConverter = findApplicable(virtualFile)
      return textConverter.convertToSaveTextToFile(text, virtualFile)
    }

    @JvmStatic
    fun convertToLoadDocumentTextFromFile(text: CharSequence, virtualFile: VirtualFile): CharSequence {
      val textConverter = findApplicable(virtualFile)
      return textConverter.convertToLoadDocumentFromFile(text, virtualFile)
    }

    @JvmStatic
    fun updateFileSizeLoadLimit(virtualFile: VirtualFile, limit: Int): Int {
      val textConverter = findApplicable(virtualFile)
      return textConverter.getFileSizeLoadLimit(virtualFile, limit)
    }

    private fun findApplicable(virtualFile: VirtualFile) =
      EP.extensionsIfPointIsRegistered.firstOrNull { it.isApplicable(virtualFile) } ?: DefaultFileTextConverter()
  }

  fun getFileSizeLoadLimit(virtualFile: VirtualFile, limit: Int): Int
}