// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.mocks

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.indexing.SingleEntryFileBasedIndexExtension
import com.intellij.util.indexing.SingleEntryIndexer
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.IOUtil
import java.io.DataInput
import java.io.DataOutput
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

abstract class ConfigurableFileIndexerBase : SingleEntryFileBasedIndexExtension<String>() {
  private val INDEX_ID = ID.create<Int, String>("com.intellij.util.indexing.mocks.ConfigurableFileIndexerBase.${cnt++}")

  var indexValue: (FileContent) -> String? = { "hello" }
  var indexVersion: Int = 1
  var additionalInputFilter: (VirtualFile) -> Boolean = { true }
  val indexedFiles: Queue<VirtualFile> = ConcurrentLinkedQueue()

  private val dataExternalizer = object : DataExternalizer<String> {
    override fun save(out: DataOutput, value: String?) = IOUtil.writeString(value, out)
    override fun read(`in`: DataInput): String = IOUtil.readString(`in`)
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
  fun getAndResetIndexedFiles(): List<VirtualFile> {
    return indexedFiles.toList().also {
      indexedFiles.clear()
    }
  }

  override fun toString(): String {
    return "${javaClass.simpleName}: (INDEX_ID=$INDEX_ID, indexVersion=$indexVersion, content=${dependsOnFileContent()})"
  }

  companion object {
    private var cnt: Int = 0
  }
}