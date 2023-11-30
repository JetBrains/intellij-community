// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.hints

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.FileBasedIndex

object RejectAllIndexingHint : FileBasedIndex.InputFilter {
  override fun acceptInput(file: VirtualFile): Boolean = false
}