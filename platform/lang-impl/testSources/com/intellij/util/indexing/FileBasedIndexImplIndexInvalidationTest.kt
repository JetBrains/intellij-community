// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.indexing.impl.IndexStorage
import com.intellij.util.indexing.impl.forward.ForwardIndex
import com.intellij.util.indexing.impl.forward.ForwardIndexAccessor
import com.intellij.util.indexing.storage.VfsAwareIndexStorageLayout
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorIntegerDescriptor
import com.intellij.util.io.KeyDescriptor
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

@TestApplication
@Suppress("SuspiciousPackagePrivateAccess")
internal class FileBasedIndexImplIndexInvalidationTest {
  @Test
  fun `version mismatch clears the layout before index creation`() {
    val events = mutableListOf<String>()
    val layout = TestLayout { events.add("clear") }
    val extension = TestIndexExtension { actualLayout ->
      assertThat(actualLayout).isSameAs(layout)
      events.add("create")
      mock()
    }

    IndexVersion.rewriteVersion(TestIndexExtension.INDEX_ID, TestIndexExtension.VERSION - 1)
    try {
      FileBasedIndexImpl.registerIndexer(
        extension,
        layout,
        IndexConfiguration(),
        IndexVersionRegistrationSink(),
        IntOpenHashSet(),
      )

      assertThat(events).containsExactly("clear", "create")
    }
    finally {
      FileUtil.deleteWithRenaming(IndexInfrastructure.getPersistentIndexRootDir(TestIndexExtension.INDEX_ID))
      FileUtil.deleteWithRenaming(IndexInfrastructure.getIndexRootDir(TestIndexExtension.INDEX_ID))
    }
  }

  private class TestIndexExtension(
    private val createIndex: (VfsAwareIndexStorageLayout<Int, Int>) -> UpdatableIndex<Int, Int, FileContent, *>,
  ) : FileBasedIndexExtension<Int, Int>(), CustomImplementationFileBasedIndexExtension<Int, Int> {
    override fun getName(): ID<Int, Int> = INDEX_ID

    override fun getVersion(): Int = VERSION

    override fun dependsOnFileContent(): Boolean = true

    override fun getIndexer(): DataIndexer<Int, Int, FileContent> = DataIndexer { emptyMap() }

    override fun getKeyDescriptor(): KeyDescriptor<Int> = EnumeratorIntegerDescriptor.INSTANCE

    override fun getValueExternalizer(): DataExternalizer<Int> = EnumeratorIntegerDescriptor.INSTANCE

    override fun getInputFilter(): FileBasedIndex.InputFilter = FileBasedIndex.InputFilter { true }

    override fun createIndexImplementation(
      extension: FileBasedIndexExtension<Int, Int>,
      indexStorageLayout: VfsAwareIndexStorageLayout<Int, Int>,
      isInitialBuild: Boolean,
    ): UpdatableIndex<Int, Int, FileContent, *> = createIndex(indexStorageLayout)

    companion object {
      val INDEX_ID: ID<Int, Int> = ID.create("FileBasedIndexImplIndexInvalidationTest")
      const val VERSION: Int = 42
    }
  }

  private class TestLayout(private val clear: () -> Unit) : VfsAwareIndexStorageLayout<Int, Int> {
    override fun openIndexStorage(): IndexStorage<Int, Int> = error("The custom index does not open the layout storage")

    override fun openForwardIndex(): ForwardIndex = error("The custom index does not open the forward index")

    override fun getForwardIndexAccessor(): ForwardIndexAccessor<Int, Int> = error("The custom index does not use a forward accessor")

    override fun clearIndexData() = clear()
  }
}
