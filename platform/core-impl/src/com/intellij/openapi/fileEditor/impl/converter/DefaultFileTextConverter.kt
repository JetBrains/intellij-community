// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.converter

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class DefaultFileTextConverter : FileTextConverter {
  override fun isApplicable(virtualFile: VirtualFile): Boolean = true

  override fun convertToSaveTextToFile(text: String, virtualFile: VirtualFile): String = text

  override fun convertToLoadDocumentFromFile(text: CharSequence, virtualFile: VirtualFile): CharSequence = text

  override fun getFileSizeLoadLimit(virtualFile: VirtualFile, limit: Int): Int = limit
}