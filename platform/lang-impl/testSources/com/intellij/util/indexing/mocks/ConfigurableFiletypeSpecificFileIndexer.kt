// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.mocks

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter
import com.intellij.util.indexing.FileBasedIndex

open class ConfigurableFiletypeSpecificFileIndexer(val filetype: FileType) : ConfigurableFileIndexerBase() {
  private val fileTypeAwareInputFilter = object : DefaultFileTypeSpecificInputFilter(filetype) {
    override fun acceptInput(file: VirtualFile): Boolean {
      return additionalInputFilter(file) && super.acceptInput(file)
    }
  }

  override fun getInputFilter(): FileBasedIndex.InputFilter = fileTypeAwareInputFilter
}
