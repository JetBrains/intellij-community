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

class ConfigurableTextFileIndexer : SingleEntryFileBasedIndexExtension<String>() {
  companion object {
    private var cnt: Int = 0
  }

  private val INDEX_ID = ID.create<Int, String>("com.intellij.util.indexing.mocks.ConfigurableTextFileIndexer.${cnt++}")

  var indexValue: (FileContent) -> String? = { "hello" }
  var indexVersion: Int = 1
  var additionalInputFilter: (VirtualFile) -> Boolean = { true }
  val indexedFiles: Queue<VirtualFile> = ConcurrentLinkedQueue()

  private val dataExternalizer = object : DataExternalizer<String> {
    override fun save(out: DataOutput, value: String?) = IOUtil.writeString(value, out)
    override fun read(`in`: DataInput): String = IOUtil.readString(`in`)
  }

  private val fileTypeAwareInputFilter = object : DefaultFileTypeSpecificInputFilter(PlainTextFileType.INSTANCE) {
    override fun acceptInput(file: VirtualFile): Boolean {
      return additionalInputFilter(file) && super.acceptInput(file)
    }
  }

  private val singleEntryIndexer = object : SingleEntryIndexer<String>(true) {
    override fun computeValue(inputData: FileContent): String? {
      indexedFiles.add(inputData.file)
      return indexValue(inputData)
    }
  }

  override fun getName(): ID<Int, String> = INDEX_ID
  override fun getIndexer(): SingleEntryIndexer<String> = singleEntryIndexer
  override fun getValueExternalizer(): DataExternalizer<String> = dataExternalizer
  override fun getVersion(): Int = indexVersion
  override fun getInputFilter(): FileBasedIndex.InputFilter = fileTypeAwareInputFilter
  fun getAndResetIndexedFiles(): List<VirtualFile> {
    return indexedFiles.toList().also {
      indexedFiles.clear()
    }
  }
}