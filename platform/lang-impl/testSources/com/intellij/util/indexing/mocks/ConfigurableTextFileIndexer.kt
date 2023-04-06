// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.mocks

import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.*
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.IOUtil
import java.io.DataInput
import java.io.DataOutput
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

open class ConfigurableTextFileIndexer : ConfigurableFileIndexerBase() {
  private val fileTypeAwareInputFilter = object : DefaultFileTypeSpecificInputFilter(PlainTextFileType.INSTANCE) {
    override fun acceptInput(file: VirtualFile): Boolean {
      return additionalInputFilter(file) && super.acceptInput(file)
    }
  }

  override fun getInputFilter(): FileBasedIndex.InputFilter = fileTypeAwareInputFilter
}