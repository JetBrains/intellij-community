// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing

import com.intellij.platform.testFramework.loadExtensionWithText
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.EnumeratorIntegerDescriptor
import com.intellij.util.io.KeyDescriptor
import java.util.concurrent.atomic.AtomicInteger

@InternalIgnoreDependencyViolation
internal class CountingFileBasedIndexExtension : CountingIndexBase("counting.file.based.index", true) {
  companion object {
    @JvmStatic
    val INDEX_ID: ID<Int, Void>
      get() = INSTANCE.name

    @JvmStatic
    val COUNTER: AtomicInteger
      get() = INSTANCE.counter

    @JvmStatic
    val INSTANCE: CountingFileBasedIndexExtension
      get() = EXTENSION_POINT_NAME.findExtensionOrFail(CountingFileBasedIndexExtension::class.java)

    @JvmStatic
    fun registerCountingFileBasedIndex(testRootDisposable: Disposable): CountingFileBasedIndexExtension =
      registerCountingFileBasedIndex(CountingFileBasedIndexExtension::class.java, testRootDisposable)
  }
}

@InternalIgnoreDependencyViolation
internal class CountingContentIndependentFileBasedIndexExtension :
  CountingIndexBase("counting.content.independent.file.based.index", false) {
  override fun getDefaultValue(): Map<Int, Nothing?> = mapOf(2 to null)

  companion object {
    @JvmStatic
    fun registerCountingFileBasedIndex(testRootDisposable: Disposable): CountingContentIndependentFileBasedIndexExtension =
      registerCountingFileBasedIndex(CountingContentIndependentFileBasedIndexExtension::class.java, testRootDisposable)
  }
}

private fun <T : CountingIndexBase> registerCountingFileBasedIndex(clazz: Class<T>, testRootDisposable: Disposable): T {
  val text = "<fileBasedIndex implementation=\"${clazz.name}\"/>"
  Disposer.register(testRootDisposable, loadExtensionWithText(text))
  return ScalarIndexExtension.EXTENSION_POINT_NAME.findExtensionOrFail(clazz)
}

internal open class CountingIndexBase(id: String, private val dependsOnFileContent: Boolean) : ScalarIndexExtension<Int>() {
  internal val counter: AtomicInteger = AtomicInteger()

  override fun getIndexer(): DataIndexer<Int, Void, FileContent> {
    return DataIndexer {
      counter.incrementAndGet()
      getDefaultValue()
    }
  }

  private val name = ID.create<Int, Void>(id)
  override fun getName(): ID<Int, Void> = name
  override fun getKeyDescriptor(): KeyDescriptor<Int> = EnumeratorIntegerDescriptor.INSTANCE
  override fun getVersion(): Int = 0
  override fun getInputFilter(): FileBasedIndex.InputFilter = FileBasedIndex.InputFilter { f: VirtualFile -> f.name.contains("Foo") }
  override fun dependsOnFileContent(): Boolean = dependsOnFileContent
  open fun getDefaultValue(): Map<Int, Nothing?> = mapOf(1 to null)
}