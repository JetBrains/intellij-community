// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation
import com.intellij.openapi.util.Disposer
import com.intellij.platform.testFramework.loadExtensionWithText
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import com.intellij.util.io.VoidDataExternalizer
import com.intellij.util.io.DataExternalizer

internal class TracingIdFilter: IdFilter() {
  override fun containsFileId(id: Int): Boolean {
    return false
  }
}

/**
 * Sets [FileBasedIndexExtension.traceKeyHashToVirtualFileMapping] to true
 */
@InternalIgnoreDependencyViolation
internal class TracingFileBasedIndexExtension : FileBasedIndexExtension<String, Void>() {
  companion object {
    @JvmStatic
    val INDEX_ID: ID<String, Void>
      get() = INSTANCE.name

    @JvmStatic
    val INSTANCE: TracingFileBasedIndexExtension
      get() = EXTENSION_POINT_NAME.findExtensionOrFail(TracingFileBasedIndexExtension::class.java)

    @JvmStatic
    fun registerTracingFileBasedIndex(testRootDisposable: Disposable): TracingFileBasedIndexExtension {
      val text = "<fileBasedIndex implementation=\"${TracingFileBasedIndexExtension::class.java.name}\"/>"
      Disposer.register(testRootDisposable, loadExtensionWithText(text))
      return EXTENSION_POINT_NAME.findExtensionOrFail(TracingFileBasedIndexExtension::class.java)
    }

    fun getIdFilter(): TracingIdFilter {
      return TracingIdFilter()
    }
  }

  private val name = ID.create<String, Void>("tracing.file.based.index")

  override fun getName(): ID<String, Void> = name

  override fun getVersion(): Int = 0

  override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

  override fun getInputFilter(): FileBasedIndex.InputFilter = FileBasedIndex.InputFilter { file -> file.name.contains("Foo") }

  override fun getIndexer(): DataIndexer<String, Void, FileContent> = DataIndexer { data ->
    mapOf("test_key" to null)
  }

  override fun dependsOnFileContent(): Boolean = true

  override fun traceKeyHashToVirtualFileMapping(): Boolean = true

  override fun getValueExternalizer(): DataExternalizer<Void> = VoidDataExternalizer.INSTANCE
}
