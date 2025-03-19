// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.hints

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
object AcceptAllFilesAndDirectoriesIndexingHint : FileBasedIndex.InputFilter {
  override fun acceptInput(file: VirtualFile): Boolean = true
}