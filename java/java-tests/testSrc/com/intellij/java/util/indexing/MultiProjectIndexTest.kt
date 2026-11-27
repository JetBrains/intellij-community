// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.util.indexing

import com.intellij.find.ngrams.TrigramIndex
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.testFramework.loadExtensionWithText
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.psi.stubs.StubUpdatingIndex
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.rules.TempDirectoryExtension
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileBasedIndexInfrastructureExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.indexing.IndexedFile
import com.intellij.util.indexing.UpdatableIndex
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

const val fileNameMarkerPrefix: String = "TestFoo"

@TestApplication
class MultiProjectIndexTest {
  @JvmField
  @RegisterExtension
  val tempDir: TempDirectoryExtension = TempDirectoryExtension()

  @TestDisposable
  lateinit var disposable: Disposable

  @Test
  fun `test index extension process files intersection`(): Unit = timeoutRunBlocking {
    val text = "<fileBasedIndexInfrastructureExtension implementation=\"" + CountingTestExtension::class.java.name + "\"/>"
    withContext(Dispatchers.EDT) {
      Disposer.register(disposable, loadExtensionWithText(text))
    }
    val ext = FileBasedIndexInfrastructureExtension.EP_NAME.findExtension(CountingTestExtension::class.java)!!

    val projectPath1 = tempDir.newDirectory("project1").toPath()
    val projectPath2 = tempDir.newDirectory("project2").toPath()
    val commonContentRoot = tempDir.newDirectory("common-content-root").toPath()

    commonContentRoot.resolve("${fileNameMarkerPrefix}1.txt").writeText("hidden gem")
    commonContentRoot.resolve("${fileNameMarkerPrefix}2.txt").writeText("foobar")

    val project1 = openProject(projectPath1)
    val project2 = openProject(projectPath2)

    val module1 = PsiTestUtil.addModule(project1, JavaModuleType.getModuleType(), "module1", projectPath1.toVirtualFile())
    val module2 = PsiTestUtil.addModule(project2, JavaModuleType.getModuleType(), "module2", projectPath1.toVirtualFile())

    PsiTestUtil.addContentRoot(module1, commonContentRoot.toVirtualFile())
    assertEquals(0, ext.trigramCounter.get())
    assertEquals(2, ext.stubCounter.get()) // stubs should not be build for txt files
    val commonBundledFileCount = ext.commonBundledFileCounter.get()
    PsiTestUtil.addContentRoot(module2, commonContentRoot.toVirtualFile())
    assertEquals(2, ext.trigramCounter.get())
    assertEquals(4, ext.stubCounter.get())
    assertEquals(commonBundledFileCount, ext.commonBundledFileCounter.get())

    withContext(Dispatchers.EDT) {
      ProjectManagerEx.getInstanceEx().forceCloseProject(project1)
      ProjectManagerEx.getInstanceEx().forceCloseProject(project2)
    }
    assertTrue(project1.isDisposed)
    assertTrue(project2.isDisposed)
  }

  private suspend fun openProject(path: Path): Project = withContext(Dispatchers.EDT) {
    val project = PlatformTestUtil.loadAndOpenProject(path, disposable)
    do {
      UIUtil.dispatchAllInvocationEvents() // for post-startup activities
    }
    while (DumbService.getInstance(project).isDumb)
    project
  }

  private fun Path.toVirtualFile(): VirtualFile = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(this)!!
}

@InternalIgnoreDependencyViolation
internal class CountingTestExtension : FileBasedIndexInfrastructureExtension {
  val stubCounter: AtomicInteger = AtomicInteger()
  val trigramCounter: AtomicInteger = AtomicInteger()
  val commonBundledFileCounter: AtomicInteger = AtomicInteger()

  override fun createFileIndexingStatusProcessor(project: Project): FileBasedIndexInfrastructureExtension.FileIndexingStatusProcessor {
    return object : FileBasedIndexInfrastructureExtension.FileIndexingStatusProcessor {
      override fun shouldProcessUpToDateFiles(): Boolean = true

      override fun processUpToDateFile(file: IndexedFile, inputId: Int, indexId: ID<*, *>): Boolean {
        if (file.fileName.startsWith(fileNameMarkerPrefix)) {
          if (indexId == TrigramIndex.INDEX_ID) {
            trigramCounter.incrementAndGet()
          }
          else if (indexId == StubUpdatingIndex.INDEX_ID) {
            stubCounter.incrementAndGet()
          }
        }
        if (file.fileName == "svg20.rnc" && indexId == TrigramIndex.INDEX_ID) {
          commonBundledFileCounter.incrementAndGet()
        }
        return true
      }

      override fun tryIndexFileWithoutContent(file: IndexedFile, inputId: Int, indexId: ID<*, *>): Boolean = false

      override fun hasIndexForFile(file: VirtualFile, inputId: Int, extension: FileBasedIndexExtension<*, *>): Boolean = false
    }
  }

  override fun <K : Any?, V : Any?> combineIndex(indexExtension: FileBasedIndexExtension<K, V>,
                                                 baseIndex: UpdatableIndex<K, V, FileContent, *>): UpdatableIndex<K, V, FileContent, *> {
    return baseIndex
  }

  override fun onFileBasedIndexVersionChanged(indexId: ID<*, *>) {}

  override fun onStubIndexVersionChanged(indexId: StubIndexKey<*, *>) {}

  override fun initialize(indexLayoutId: String?): FileBasedIndexInfrastructureExtension.InitializationResult =
    FileBasedIndexInfrastructureExtension.InitializationResult.SUCCESSFULLY

  override fun attachData(project: Project) {}

  override fun resetPersistentState() {}

  override fun resetPersistentState(indexId: ID<*, *>) {}

  override fun shutdown() {}

  override fun getVersion(): Int = 0

}