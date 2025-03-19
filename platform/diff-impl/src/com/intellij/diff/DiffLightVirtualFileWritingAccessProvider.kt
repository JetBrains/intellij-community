// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff

import com.intellij.openapi.editor.EditorBundle
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.WritingAccessProvider
import org.jetbrains.annotations.Nls

internal class DiffLightVirtualFileWritingAccessProvider : WritingAccessProvider() {
  override fun requestWriting(files: Collection<VirtualFile>): Collection<VirtualFile> =
    files.filter { it is DiffLightVirtualFile && !it.isWritable() }

  override fun isPotentiallyWritable(file: VirtualFile): Boolean = file !is DiffLightVirtualFile || file.isWritable()

  @Nls
  override fun getReadOnlyMessage(): String = EditorBundle.message("editing.viewer.hint")
}

internal interface DiffLightVirtualFile {
  fun isWritable(): Boolean
}