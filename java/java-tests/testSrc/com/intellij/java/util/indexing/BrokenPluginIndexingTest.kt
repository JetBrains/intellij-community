// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.util.indexing

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.platform.testFramework.loadExtensionWithText
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.javaModuleCodeInsightFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.indexing.ScalarIndexExtension
import com.intellij.util.indexing.diagnostic.BrokenIndexingDiagnostics
import com.intellij.util.indexing.impl.MapReduceIndexMappingException
import com.intellij.util.io.EnumeratorIntegerDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicReference

@TestApplication
class BrokenPluginIndexingTest {
  private val myFixture by javaModuleCodeInsightFixture(PathManager.getHomePath())
  private val project get() = myFixture.project

  @Test
  fun `test broken plugin indexer is reported`(): Unit = timeoutRunBlocking {
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
    Disposer.register(myFixture.testRootDisposable, withContext(Dispatchers.EDT) { loadExtensionWithText(text) })
    val psiClass = myFixture.addClass("class Some {}")
    val file = readAction { psiClass.containingFile.virtualFile }
    readAction { FileBasedIndex.getInstance().getFileData(BrokenFileBasedIndexExtension.INDEX_ID, file, project) }

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