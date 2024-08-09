// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.find.TextSearchService
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.impl.ProjectUtilCore
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.*
import com.intellij.openapi.components.service
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCachePath
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ShutDownTracker
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.writeText
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.*
import com.intellij.testFramework.LightProjectDescriptor.TEST_MODULE_NAME
import com.intellij.testFramework.TestActionEvent.createTestEvent
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.utils.vfs.createFile
import com.intellij.util.CommonProcessors
import com.intellij.util.SystemProperties
import com.intellij.util.indexing.PersistentDirtyFilesQueue.getQueueFile
import com.intellij.util.indexing.PersistentDirtyFilesQueue.getQueuesDir
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper.Companion.readJsonIndexingActivityDiagnostic
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumperUtils
import com.intellij.util.indexing.diagnostic.dto.JsonIndexingActivityDiagnostic
import com.intellij.util.indexing.diagnostic.dto.JsonProjectScanningHistory
import com.intellij.util.indexing.diagnostic.dto.JsonProjectScanningHistoryTimes
import com.intellij.util.indexing.mocks.FakeFileType
import com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.random.Random

/**
 * See also com.intellij.functionalTests.FunctionalDirtyFilesQueueTest
 */
@RunWith(JUnit4::class)
class DirtyFilesQueueTest {
  companion object {
    @ClassRule
    @JvmField
    val appRule: ApplicationRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val tempDir: TemporaryDirectory = TemporaryDirectory()
  val nameToPathMap = mutableMapOf<String, Path>()

  @Rule
  @JvmField
  val testNameRule = TestName()

  private lateinit var testRootDisposable: CheckedDisposable

  @Before
  fun setup() {
    testRootDisposable = Disposer.newCheckedDisposable("DirtyFilesQueueTest")
    ShutDownTracker.getInstance().registerShutdownTask { // delete files after they are persisted by FileBasedIndexImpl.performShutdown
      FileUtil.delete(getQueueFile())
      FileUtil.deleteRecursively(getQueuesDir())
    }
  }

  @After
  fun tearDown() {
    runBlocking(Dispatchers.EDT) {
      writeAction {
        Disposer.dispose(testRootDisposable) // must dispose in EDT
      }
    }
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
      openProject(testNameRule.methodName) { project, module ->
        val src = tempDir.createVirtualDir("src")
        writeAction {
          val rootModel = ModuleRootManager.getInstance(module).modifiableModel
          rootModel.addContentEntry(src)
          rootModel.commit()
        }
        IndexingTestUtil.waitUntilIndexesAreReady(project) // scanning due to model change
        writeAction { src.createFile("A.txt") }
        restart(skipFullScanning = false, project) // persist queue
        assertThat(project.getQueueFile()).exists()
        assertThat(getQueueFile()).exists()
        runBlocking(Dispatchers.EDT) {
          writeIntentReadAction {
            ForceIndexRebuildAction().actionPerformed(createTestEvent())
          }
        }
        IndexingTestUtil.suspendUntilIndexesAreReady(project)
        assertThat(project.getQueueFile()).doesNotExist()
        assertThat(getQueueFile()).doesNotExist()
      }
    }
  }

  @Test
  fun `test file is indexed after it was edited when project was closed (restart app)`() {
    doTestFileIsIndexedAfterItWasEditedWhenProjectWasClosed(fileCount = 5, expectFullScanning = false, restartApp = true)
  }

  @Test
  fun `test file is indexed after it was edited when project was closed (don't restart app)`() {
    doTestFileIsIndexedAfterItWasEditedWhenProjectWasClosed(fileCount = 5, expectFullScanning = false, restartApp = false)
  }

  @Test
  fun `test file is indexed after it was edited when project was closed (with full scanning using mod count)`() {
    setOrphanDirtyFilesQueueMaxSize(5)
    doTestFileIsIndexedAfterItWasEditedWhenProjectWasClosed(fileCount = 30, expectFullScanning = true, restartApp = true)
  }

  private fun setOrphanDirtyFilesQueueMaxSize(@Suppress("SameParameterValue") value: Int) {
    Registry.get("maximum.size.of.orphan.dirty.files.queue").setValue(value, testRootDisposable)
  }

  private fun doTestFileIsIndexedAfterItWasEditedWhenProjectWasClosed(fileCount: Int, expectFullScanning: Boolean, restartApp: Boolean) {
    runBlocking {
      val fileNames = (0 until fileCount).map { "A$it.txt" }
      val commonPrefix1 = "common_prefix_1_" + (0 until 10).map { Random.nextInt('A'.code, 'Z'.code).toChar() }.joinToString("")
      val commonPrefix2 = "common_prefix_2_" + (0 until 10).map { Random.nextInt('A'.code, 'Z'.code).toChar() }.joinToString("")

      val files: List<VirtualFile> = openProject(testNameRule.methodName) { project, module ->
        val src = tempDir.createVirtualDir("src")
        writeAction {
          val rootModel = ModuleRootManager.getInstance(module).modifiableModel
          rootModel.addContentEntry(src)
          rootModel.commit()
        }
        IndexingTestUtil.waitUntilIndexesAreReady(project) // scanning due to model change
        val files = writeAction {
          fileNames.map {
            val file = src.createFile(it)
            file.writeText("$commonPrefix1 $it")
            file
          }
        }
        smartReadAction(project) {
          val foundFiles = findFilesWithText(commonPrefix1, project)
          assertThat(foundFiles).containsAll(files)
        }
        files
      }
      writeAction {
        for (file in files) {
          file.writeText("$commonPrefix2 $file")
        }
      } // add files to orphan queue
      if (restartApp) {
        restart(skipFullScanning = true) // persist orphan queue
      }
      openProject(testNameRule.methodName) { project, _ ->
        smartReadAction(project) {
          val foundFiles = findFilesWithText(commonPrefix2, project)
          assertThat(foundFiles).containsAll(files)
        }

        writeIntentReadAction {
          IndexDiagnosticDumper.getInstance().waitAllActivitiesAreDumped()
          val scanning = findScanningTriggeredBy(project, ReopeningType.PROJECT_REOPEN)
          assertIsFullScanning(scanning, expectFullScanning)
          if (!expectFullScanning) {
            assertCameFromOrphanQueue(scanning, fileNames)
          }
        }
      }
    }
  }

  private fun findFilesWithText(text: String, project: Project): Collection<VirtualFile> {
    val service = ApplicationManager.getApplication().service<TextSearchService>()
    val processor = CommonProcessors.CollectProcessor<VirtualFile>()
    service.processFilesWithText(text, processor, GlobalSearchScope.allScope(project))
    return processor.results
  }

  private fun assertCameFromOrphanQueue(scanning: JsonIndexingActivityDiagnostic, fileNames: List<String>) {
    val stats = (scanning.projectIndexingActivityHistory as JsonProjectScanningHistory).scanningStatistics
      .first { it.providerName == "dirty files iterator (from orphan queue=true)" }
    assertThat(fileNames).allMatch { name ->
      stats.scannedFiles!!.any { it.path.presentablePath.endsWith("/$name") }
    }
  }

  private fun configureModule(project: Project): Module {
    var m: Module? = null
    runBlocking(Dispatchers.EDT) {
      LightProjectDescriptor().setUpProject(project, object : LightProjectDescriptor.SetupHandler {
        override fun moduleCreated(module: Module) {
          m = module
        }
      })
    }
    return m!!
  }

  private fun testDirtyFileIsIndexedAfterFileBasedIndexIsRestarted(skipFullScanning: Boolean) {
    runBlocking {
      openProject(testNameRule.methodName) { project, module ->
        val fileBasedIndex = FileBasedIndex.getInstance() as FileBasedIndexImpl
        val filetype = FakeFileType()
        registerFiletype(filetype)
        val src = tempDir.createVirtualDir("src")

        writeAction {
          val rootModel = ModuleRootManager.getInstance(module).modifiableModel
          rootModel.addContentEntry(src)
          rootModel.commit()
        }
        IndexingTestUtil.waitUntilIndexesAreReady(project) // scanning due to model change
        val file = writeAction {
          src.createFile("A.${filetype.defaultExtension}")
        }
        fileBasedIndex.changedFilesCollector.ensureUpToDate()
        assertThat(fileBasedIndex.getAllDirtyFiles(project)).contains((file as VirtualFileWithId).id)
        restart(skipFullScanning, project)
        assertFullScanning(project, !skipFullScanning, ReopeningType.TUMBLER)
        smartReadAction(project) {
          val files = FileTypeIndex.getFiles(filetype, GlobalSearchScope.allScope(project))
          assertThat(files).contains(file)
        }
      }
    }
  }

  @Test
  fun `test removed dirty file is removed from indexes after FileBasedIndex is restarted (skip full scanning)`() {
    runBlocking {
      openProject(testNameRule.methodName) { project, module ->
        val fileBasedIndex = FileBasedIndex.getInstance() as FileBasedIndexImpl
        val filetype = FakeFileType()
        registerFiletype(filetype)
        val src = tempDir.createVirtualDir("src")

        val file = writeAction {
          val rootModel = ModuleRootManager.getInstance(module).modifiableModel
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
        restart(skipFullScanning = true, project)
        assertFullScanning(project, false, ReopeningType.TUMBLER)
        smartReadAction(project) {
          val files = FileTypeIndex.getFiles(filetype, GlobalSearchScope.allScope(project))
          assertThat(files).doesNotContain(file)
        }
      }
    }
  }

  private suspend fun <T> openProject(name: String, action: suspend (Project, Module) -> T): T {
    val projectFile = nameToPathMap.computeIfAbsent(name) { n -> TemporaryDirectory.generateTemporaryPath("project_$n") }
    val reopenProject = ProjectUtilCore.isValidProjectPath(projectFile)
    projectFile.createDirectories()
    @Suppress("DATA_CLASS_INVISIBLE_COPY_USAGE_WARNING") val options = createTestOpenProjectOptions().copy(projectName = name)
    SystemProperties.setProperty("intellij.indexes.diagnostics.should.dump.paths.of.indexed.files", "true")
    IndexDiagnosticDumper.shouldDumpInUnitTestMode = true
    WorkspaceModelCacheImpl.forceEnableCaching(testRootDisposable)
    val project = ProjectUtil.openOrImportAsync(projectFile, options)!!
    val module = if (reopenProject) ModuleManager.getInstance(project).findModuleByName(TEST_MODULE_NAME)!! else configureModule(project)
    return project.useProjectAsync(save = true) {
      IndexingTestUtil.waitUntilIndexesAreReady(project)
      val res = action(project, module)
      IndexingTestUtil.suspendUntilIndexesAreReady(project)
      IndexDiagnosticDumper.getInstance().waitAllActivitiesAreDumped()
      IndexDiagnosticDumper.shouldDumpInUnitTestMode = false
      FileUtil.deleteRecursively(project.getProjectCachePath(IndexDiagnosticDumperUtils.indexingDiagnosticDir))
      SystemProperties.setProperty("intellij.indexes.diagnostics.should.dump.paths.of.indexed.files", "false")
      res
    }
  }

  private fun restart(skipFullScanning: Boolean, project: Project? = null) {
    runBlocking(Dispatchers.EDT) {
      writeIntentReadAction {
        val tumbler = FileBasedIndexTumbler("test")
        if (skipFullScanning) {
          tumbler.allowSkippingFullScanning()
        }
        tumbler.turnOff()
        tumbler.turnOn()
      }
      if (project != null) {
        IndexingTestUtil.suspendUntilIndexesAreReady(project)
      }
    }
  }

  private enum class ReopeningType(val reason: String) {
    TUMBLER("FileBasedIndexTumbler"),
    PROJECT_REOPEN("On project open")
  }

  private fun assertFullScanning(project: Project, fullScanning: Boolean, @Suppress("SameParameterValue") reopeningType: ReopeningType) {
    IndexDiagnosticDumper.getInstance().waitAllActivitiesAreDumped()

    val scanning = findScanningTriggeredBy(project, reopeningType)
    assertIsFullScanning(scanning, fullScanning)
  }

  private fun assertIsFullScanning(scanning: JsonIndexingActivityDiagnostic, fullScanning: Boolean) {
    val times = (scanning.projectIndexingActivityHistory.times as JsonProjectScanningHistoryTimes)
    assertThat(times.scanningType.isFull).isEqualTo(fullScanning)
  }

  private fun findScanningTriggeredBy(project: Project, reopeningType: ReopeningType): JsonIndexingActivityDiagnostic {
    val projectDir = project.getProjectCachePath(IndexDiagnosticDumperUtils.indexingDiagnosticDir)
    val diagnostics = projectDir.toFile().listFiles()!!
      .filter { it.extension == "json" }
      .mapNotNull { readJsonIndexingActivityDiagnostic(it.toPath()) }
      .filter {
        val times = it.projectIndexingActivityHistory.times
        times is JsonProjectScanningHistoryTimes && times.scanningReason?.contains(reopeningType.reason) == true
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
