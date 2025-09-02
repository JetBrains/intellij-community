// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing

import com.intellij.platform.testFramework.loadExtensionWithText
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.util.indexing.diagnostic.BrokenIndexingDiagnostics
import com.intellij.util.indexing.impl.MapReduceIndexMappingException
import com.intellij.util.io.EnumeratorIntegerDescriptor
import java.util.concurrent.atomic.AtomicReference

class BrokenPluginIndexingTest : JavaCodeInsightFixtureTestCase() {
  fun `test broken plugin indexer is reported`() {
    data class CallParams(
      val fileId: Int,
      val file: VirtualFile?,
      val fileType: FileType?,
      val indexId: ID<*, *>,
      val exception: MapReduceIndexMappingException
    )
    val callParamsRef = AtomicReference<CallParams>()

    BrokenIndexingDiagnostics.exceptionListener = object : BrokenIndexingDiagnostics.Listener {
      override fun onFileIndexMappingFailed(
        fileId: Int,
        file: VirtualFile?,
        fileType: FileType?,
        indexId: ID<*, *>,
        exception: MapReduceIndexMappingException
      ) {
        callParamsRef.set(CallParams(fileId, file, fileType, indexId, exception))
      }
    }

    val text = "<fileBasedIndex implementation=\"" + BrokenFileBasedIndexExtension::class.qualifiedName + "\"/>"
    Disposer.register(testRootDisposable, loadExtensionWithText(text))
    val file = myFixture.addClass("class Some {}").containingFile.virtualFile
    FileBasedIndex.getInstance().getFileData(BrokenFileBasedIndexExtension.INDEX_ID, file, project)

    val callParams = callParamsRef.get()
    assertEquals((file as VirtualFileWithId).id, callParams.fileId)
    assertEquals(file, callParams.file)
    assertEquals(FileTypeRegistry.getInstance().findFileTypeByName("JAVA"), callParams.fileType)
    assertEquals(BrokenFileBasedIndexExtension.INDEX_ID, callParams.indexId)
    assertEquals(BrokenFileBasedIndexExtension::class.java, callParams.exception.classToBlame)
  }
}

internal class BrokenPluginException : RuntimeException()

@InternalIgnoreDependencyViolation
internal class BrokenFileBasedIndexExtension : ScalarIndexExtension<Int>() {
  override fun getIndexer(): DataIndexer<Int, Void, FileContent> = DataIndexer<Int, Void, FileContent> { throw BrokenPluginException() }
  override fun getName(): ID<Int, Void> = INDEX_ID
  override fun getKeyDescriptor(): EnumeratorIntegerDescriptor = EnumeratorIntegerDescriptor.INSTANCE!!
  override fun getVersion(): Int = 0
  override fun getInputFilter(): FileBasedIndex.InputFilter = FileBasedIndex.InputFilter { it.name.contains("Some") }
  override fun dependsOnFileContent(): Boolean = true

  companion object {
    @JvmStatic
    val INDEX_ID: ID<Int, Void> = ID.create("broken.file.based.index")
  }
}