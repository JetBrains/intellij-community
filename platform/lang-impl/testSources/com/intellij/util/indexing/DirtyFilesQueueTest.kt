// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.find.TextSearchService
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.platform.testFramework.loadExtensionWithText
import com.intellij.openapi.application.*
import com.intellij.openapi.components.service
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCachePath
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ShutDownTracker
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.writeText
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
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
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import com.intellij.util.indexing.testEntities.TestModuleEntitySource
import com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.rules.TestName
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.random.Random

private class DoNoRethrowBrokenIndexingErrors : LoggedErrorProcessor() {
  override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> {
    if ("#com.intellij.util.indexing.diagnostic.BrokenIndexingDiagnostics" == category) {
      return setOf(Action.LOG, Action.STDERR)
    }
    return super.processError(category, message, details, t)
  }
}

/**
 * See also com.intellij.functionalTests.FunctionalDirtyFilesQueueTest
 */

//@RunWith(JUnit4::class)
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
    runInEdtAndWait {
      Disposer.dispose(testRootDisposable) // must dispose in EDT
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
        module.createContentRoot(project, src)
        IndexingTestUtil.waitUntilIndexesAreReady(project) // scanning due to model change
        edtWriteAction { src.createFile("A.txt") }
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

  private suspend fun ModuleEntity.createContentRoot(
    project: Project,
    root: VirtualFile,
  ) {
    val workspaceModel = project.workspaceModel
    val url = root.toVirtualFileUrl(workspaceModel.getVirtualFileUrlManager())
    val contentRoot = ContentRootEntity(url = url, excludedPatterns = emptyList(), entitySource = entitySource)
    workspaceModel.update("add content root") { storage ->
      storage.modifyModuleEntity(this) {
        this.contentRoots += contentRoot
      }
    }
  }

  internal class BadFileBasedIndexExtension : FileBasedIndexExtension<String, String>() {
    companion object {
      private val INDEX_ID: ID<String, String> = ID.create("badIndex")

      @Volatile
      internal var fails: Boolean = true
    }

    override fun getName(): ID<String, String> = INDEX_ID

    override fun getInputFilter(): FileBasedIndex.InputFilter =
      FileBasedIndex.InputFilter { file -> file.extension == FakeFileType().extension }

    override fun dependsOnFileContent(): Boolean = true

    override fun getIndexer(): DataIndexer<String?, String?, FileContent?> = DataIndexer<String?, String?, FileContent?> {
      when {
        fails -> error("Bad file failed to index")
        else -> mapOf("key" to "value")
      }
    }

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE
    override fun getValueExternalizer(): DataExternalizer<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getVersion(): Int = 0
  }

  @Test
  fun `test file is failed to index at startup but indexed after restart`() = LoggedErrorProcessor.executeWith<Throwable>(DoNoRethrowBrokenIndexingErrors()) {
    val filetype = FakeFileType()
    val text = "<fileBasedIndex implementation=\"${BadFileBasedIndexExtension::class.java.name}\"/>"

    runBlocking {
      runInEdt {
        val child = loadExtensionWithText(text)
        Disposer.register(testRootDisposable, child)
        ScalarIndexExtension.EXTENSION_POINT_NAME.findExtensionOrFail(BadFileBasedIndexExtension::class.java)
      }

      openProject(testNameRule.methodName) { project, module ->
        val fileBasedIndex = FileBasedIndex.getInstance() as FileBasedIndexImpl
        registerFiletype(filetype)
        val src = tempDir.createVirtualDir("src")

        module.createContentRoot(project, src)
        IndexingTestUtil.waitUntilIndexesAreReady(project) // scanning due to model change
        edtWriteAction {
          src.createFile("A.${filetype.defaultExtension}")
        }

        val keys = smartReadAction(project = project) {
          fileBasedIndex.getAllKeys(BadFileBasedIndexExtension().name, project)
        }
        assertThat(keys).isEmpty()
      }

      BadFileBasedIndexExtension.fails = false
      restart(skipFullScanning = true)

      openProject(testNameRule.methodName) { project, module ->
        val fileBasedIndex = FileBasedIndex.getInstance() as FileBasedIndexImpl
        val allKeys = smartReadAction(project = project) {
          fileBasedIndex.getAllKeys(BadFileBasedIndexExtension().name, project)
        }
        assertThat(allKeys).isNotEmpty()
      }
    }
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
        module.createContentRoot(project, src)
        IndexingTestUtil.waitUntilIndexesAreReady(project) // scanning due to model change
        val files = edtWriteAction {
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
      edtWriteAction {
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

  private suspend fun configureModule(project: Project): ModuleEntity {
    val workspaceModel = project.workspaceModel
    val moduleEntity = ModuleEntity(
      name = TEST_MODULE_NAME,
      dependencies = emptyList(),
      entitySource = TestModuleEntitySource
    )

    workspaceModel.update("creating test module") { storage ->
      storage.addEntity(moduleEntity)
    }
    return workspaceModel.currentSnapshot.resolve(ModuleId(TEST_MODULE_NAME))!!
  }

  private fun testDirtyFileIsIndexedAfterFileBasedIndexIsRestarted(skipFullScanning: Boolean) {
    runBlocking {
      openProject(testNameRule.methodName) { project, module ->
        val fileBasedIndex = FileBasedIndex.getInstance() as FileBasedIndexImpl
        val filetype = FakeFileType()
        registerFiletype(filetype)
        val src = tempDir.createVirtualDir("src")

        module.createContentRoot(project, src)
        IndexingTestUtil.waitUntilIndexesAreReady(project) // scanning due to model change
        val file = edtWriteAction {
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
        module.createContentRoot(project, src)
        val file = edtWriteAction {
          src.createFile("A.${filetype.defaultExtension}")
        }
        smartReadAction(project) {
          val files = FileTypeIndex.getFiles(filetype, GlobalSearchScope.allScope(project))
          assertThat(files).contains(file)
        }
        edtWriteAction {
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

  private suspend fun <T> openProject(name: String, action: suspend (Project, ModuleEntity) -> T): T {
    val projectFile = nameToPathMap.computeIfAbsent(name) { n -> TemporaryDirectory.generateTemporaryPath("project_$n") }
    val reopenProject = ProjectUtil.isValidProjectPath(projectFile)
    projectFile.createDirectories()
    @Suppress("DATA_CLASS_INVISIBLE_COPY_USAGE_WARNING") val options = createTestOpenProjectOptions().copy(projectName = name)
    SystemProperties.setProperty("intellij.indexes.diagnostics.should.dump.paths.of.indexed.files", "true")
    IndexDiagnosticDumper.shouldDumpInUnitTestMode = true
    WorkspaceModelCacheImpl.forceEnableCaching(testRootDisposable)
    val project = ProjectUtil.openOrImportAsync(projectFile, options)!!
    val module = when {
      reopenProject -> project.workspaceModel.currentSnapshot.resolve(ModuleId(TEST_MODULE_NAME))!!
      else -> configureModule(project)
    }
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
        val tumbler = FileBasedIndexTumbler("DirtyFilesQueueTest")
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