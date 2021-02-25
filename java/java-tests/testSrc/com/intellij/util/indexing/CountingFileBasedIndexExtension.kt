// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.EnumeratorIntegerDescriptor
import com.intellij.util.io.KeyDescriptor
import java.util.concurrent.atomic.AtomicInteger

class CountingFileBasedIndexExtension : ScalarIndexExtension<Int>() {
  override fun getIndexer(): DataIndexer<Int, Void, FileContent> {
    return DataIndexer {
      COUNTER.incrementAndGet()
      mapOf(1 to null)
    }
  }

  override fun getName(): ID<Int, Void> = INDEX_ID
  override fun getKeyDescriptor(): KeyDescriptor<Int> = EnumeratorIntegerDescriptor.INSTANCE
  override fun getVersion(): Int = 0
  override fun getInputFilter(): FileBasedIndex.InputFilter = FileBasedIndex.InputFilter { f: VirtualFile -> f.name.contains("Foo") }
  override fun dependsOnFileContent(): Boolean = true

  companion object {
    @JvmStatic
    val INDEX_ID = ID.create<Int, Void>("counting.file.based.index")
    @JvmStatic
    val COUNTER = AtomicInteger()
  }
}