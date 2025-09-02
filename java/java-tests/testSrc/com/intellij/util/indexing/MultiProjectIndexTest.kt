// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.find.ngrams.TrigramIndex
import com.intellij.platform.testFramework.loadExtensionWithText
import com.intellij.openapi.extensions.InternalIgnoreDependencyViolation
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.psi.stubs.StubUpdatingIndex
import com.intellij.testFramework.*
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.ui.UIUtil
import junit.framework.TestCase
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.writeText
import kotlin.test.assertEquals

const val fileNameMarkerPrefix: String = "TestFoo"

@RunsInEdt
class MultiProjectIndexTest {
  @Rule
  @JvmField
  val tempDir: TempDirectory = TempDirectory()

  @Rule
  @JvmField
  val disposable: DisposableRule = DisposableRule()

  @Rule
  @JvmField
  val appRule: ApplicationRule = ApplicationRule()

  @Rule
  @JvmField
  val runInEdt: EdtRule = EdtRule()

  @Test
  fun `test index extension process files intersection`() {
    val text = "<fileBasedIndexInfrastructureExtension implementation=\"" + CountingTestExtension::class.java.name + "\"/>"
    Disposer.register(disposable.disposable, loadExtensionWithText(text))
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

    ProjectManagerEx.getInstanceEx().forceCloseProject(project1)
    ProjectManagerEx.getInstanceEx().forceCloseProject(project2)
    TestCase.assertTrue(project1.isDisposed)
    TestCase.assertTrue(project2.isDisposed)
  }

  private fun openProject(path: Path): Project {
    val project = PlatformTestUtil.loadAndOpenProject(path, disposable.disposable)
    do {
      UIUtil.dispatchAllInvocationEvents() // for post-startup activities
    }
    while (DumbService.getInstance(project).isDumb)
    return project
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