// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCachePath
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.TestActionEvent.createTestEvent
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.utils.vfs.createFile
import com.intellij.util.indexing.PersistentDirtyFilesQueue.getQueueFile
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper.Companion.readJsonIndexingActivityDiagnostic
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumperUtils
import com.intellij.util.indexing.diagnostic.ScanningType
import com.intellij.util.indexing.diagnostic.dto.JsonIndexingActivityDiagnostic
import com.intellij.util.indexing.diagnostic.dto.JsonProjectScanningHistoryTimes
import com.intellij.util.indexing.mocks.FakeFileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * See also [com.intellij.functionalTests.FunctionalDirtyFilesQueueTest]
 */
@RunWith(JUnit4::class)
class DirtyFilesQueueTest {
  companion object {
    @ClassRule
    @JvmField
    val p: ProjectRule = ProjectRule(true, false, null)
  }

  @Rule
  @JvmField
  val tempDir: TemporaryDirectory = TemporaryDirectory()

  private lateinit var project: Project
  private lateinit var testRootDisposable: CheckedDisposable

  @Before
  fun setup() {
    project = p.project
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    testRootDisposable = Disposer.newCheckedDisposable("DirtyFilesQueueTest")
    IndexDiagnosticDumper.shouldDumpInUnitTestMode = true
  }

  @After
  fun tearDown() {
    runBlocking(Dispatchers.EDT) {
      writeAction {
        Disposer.dispose(testRootDisposable)
      }
      IndexingTestUtil.suspendUntilIndexesAreReady(project) // scanning caused by deregistration of the file type
    }
    IndexDiagnosticDumper.getInstance().waitAllActivitiesAreDumped()
    IndexDiagnosticDumper.shouldDumpInUnitTestMode = false
    FileUtil.deleteRecursively(project.getProjectCachePath(IndexDiagnosticDumperUtils.indexingDiagnosticDir))
  }

  @Test
  fun `test dirty file is indexed after FileBasedIndex is restarted (skip full scanning)`() {
    testDirtyFileIsIndexedAfterFileBasedIndexIsRestarted(skipFullScanning = true)
  }

  @Test
  fun `test dirty file is indexed after FileBasedIndex is restarted (with full scanning)`() {
    testDirtyFileIsIndexedAfterFileBasedIndexIsRestarted(skipFullScanning = false)
  }

  @Test
  fun `test queues removed from disk after invalidating caches`() {
    runBlocking {
      val src = tempDir.createVirtualDir("src")
      writeAction {
        val rootModel = ModuleRootManager.getInstance(p.module).modifiableModel
        rootModel.addContentEntry(src)
        rootModel.commit()
      }
      IndexingTestUtil.waitUntilIndexesAreReady(project) // scanning due to model change
      writeAction { src.createFile("A.txt") }
      restartSkippingFullScanning(false) // persist queue
      assertThat(project.getQueueFile()).exists()
      assertThat(getQueueFile()).exists()
      runBlocking(Dispatchers.EDT) {
        ForceIndexRebuildAction().actionPerformed(createTestEvent())
      }
      IndexingTestUtil.suspendUntilIndexesAreReady(project)
      assertThat(project.getQueueFile()).doesNotExist()
      assertThat(getQueueFile()).doesNotExist()
    }
  }

  private fun testDirtyFileIsIndexedAfterFileBasedIndexIsRestarted(skipFullScanning: Boolean) {
    val fileBasedIndex = FileBasedIndex.getInstance() as FileBasedIndexImpl
    val filetype = FakeFileType()
    registerFiletype(filetype)
    val src = tempDir.createVirtualDir("src")

    runBlocking {
      writeAction {
        val rootModel = ModuleRootManager.getInstance(p.module).modifiableModel
        rootModel.addContentEntry(src)
        rootModel.commit()
      }
      IndexingTestUtil.waitUntilIndexesAreReady(project) // scanning due to model change
      val file = writeAction {
        src.createFile("A.${filetype.defaultExtension}")
      }
      fileBasedIndex.changedFilesCollector.ensureUpToDate()
      assertThat(fileBasedIndex.getAllDirtyFiles(project)).contains((file as VirtualFileWithId).id)
      restartSkippingFullScanning(skipFullScanning)
      assertFullScanning(!skipFullScanning)
      smartReadAction(project) {
        val files = FileTypeIndex.getFiles(filetype, GlobalSearchScope.allScope(project))
        assertThat(files).contains(file)
      }
    }
  }

  @Test
  fun `test removed dirty file is removed from indexes after FileBasedIndex is restarted (skip full scanning)`() {
    val fileBasedIndex = FileBasedIndex.getInstance() as FileBasedIndexImpl
    val filetype = FakeFileType()
    registerFiletype(filetype)
    val src = tempDir.createVirtualDir("src")

    runBlocking {
      val file = writeAction {
        val rootModel = ModuleRootManager.getInstance(p.module).modifiableModel
        rootModel.addContentEntry(src)
        rootModel.commit()
        src.createFile("A.${filetype.defaultExtension}")
      }
      smartReadAction(project) {
        val files = FileTypeIndex.getFiles(filetype, GlobalSearchScope.allScope(project))
        assertThat(files).contains(file)
      }
      writeAction {
        file.delete(this)
      }
      fileBasedIndex.changedFilesCollector.ensureUpToDate()
      assertThat(fileBasedIndex.getAllDirtyFiles(project)).contains((file as VirtualFileWithId).id)
      restartSkippingFullScanning(true)
      assertFullScanning(false)
      smartReadAction(project) {
        val files = FileTypeIndex.getFiles(filetype, GlobalSearchScope.allScope(project))
        assertThat(files).doesNotContain(file)
      }
    }
  }

  private fun restartSkippingFullScanning(skipFullScanning: Boolean) {
    runBlocking(Dispatchers.EDT) {
      val tumbler = FileBasedIndexTumbler("test")
      if (skipFullScanning) {
        tumbler.allowSkippingFullScanning()
      }
      tumbler.turnOff()
      tumbler.turnOn()
      IndexingTestUtil.suspendUntilIndexesAreReady(project)
    }
  }

  private fun assertFullScanning(fullScanning: Boolean) {
    IndexDiagnosticDumper.getInstance().waitAllActivitiesAreDumped()

    val scanning = findScanningTriggeredBy("FileBasedIndexTumbler")
    assertThat(scanning).isNotNull
    val times = (scanning.projectIndexingActivityHistory.times as JsonProjectScanningHistoryTimes)
    assertThat(times.scanningType).isEqualTo(if (fullScanning) ScanningType.FULL_ON_PROJECT_OPEN else ScanningType.PARTIAL)
  }

  private fun findScanningTriggeredBy(@Suppress("SameParameterValue") reason: String): JsonIndexingActivityDiagnostic {
    val projectDir = project.getProjectCachePath(IndexDiagnosticDumperUtils.indexingDiagnosticDir)
    val diagnostics = projectDir.toFile().listFiles()!!
      .filter { it.extension == "json" }
      .mapNotNull { readJsonIndexingActivityDiagnostic(it.toPath()) }
      .filter {
        val times = it.projectIndexingActivityHistory.times
        times is JsonProjectScanningHistoryTimes && times.scanningReason?.contains(reason) == true
      }
    assertThat(diagnostics).hasSize(1)
    return diagnostics.first()
  }

  private fun registerFiletype(filetype: FakeFileType) {
    runBlocking(Dispatchers.EDT) {
      val fileTypeManager = FileTypeManager.getInstance() as FileTypeManagerImpl
      val corePlugin = PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID)!!
      fileTypeManager.registerFileType(filetype, listOf(ExtensionFileNameMatcher(filetype.defaultExtension)), testRootDisposable, corePlugin)
    }
  }
}