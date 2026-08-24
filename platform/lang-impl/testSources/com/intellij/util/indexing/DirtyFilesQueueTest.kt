// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.find.TextSearchService
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.application.writeIntentReadAction
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
import com.intellij.platform.testFramework.loadExtensionWithText
import com.intellij.platform.workspace.jps.entities.ContentRootEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.psi.impl.cache.impl.id.IdIndex
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.LightProjectDescriptor.TEST_MODULE_NAME
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.TestActionEvent.createTestEvent
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.createTestOpenProjectOptions
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.testFramework.useProjectAsync
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
import com.intellij.util.indexing.events.FileIndexingRequest
import com.intellij.util.indexing.mocks.FakeFileType
import com.intellij.util.indexing.testEntities.TestModuleEntitySource
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.nio.file.Path
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
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

  /** Verifies that project cursors bound repeated filtering of a stale foreign request. */
  @Test
  fun `foreign dirty request is examined only once per project`() {
    assumeProjectRequestCursorFeaturesEnabled()
    runBlocking {
      openProject("${testNameRule.methodName}-A") { projectA, moduleA ->
        val srcA = tempDir.createVirtualDir("src-A")
        moduleA.createContentRoot(projectA, srcA)
        IndexingTestUtil.waitUntilIndexesAreReady(projectA)

        openProject("${testNameRule.methodName}-B") { projectB, moduleB ->
          val srcB = tempDir.createVirtualDir("src-B")
          moduleB.createContentRoot(projectB, srcB)
          IndexingTestUtil.waitUntilIndexesAreReady(projectB)

          val foreignFile = edtWriteAction {
            tempDir.createVirtualDir("outside-projects").createFile("foreign.txt")
          }
          val fileBasedIndex = FileBasedIndex.getInstance() as FileBasedIndexImpl
          fileBasedIndex.changedFilesCollector.waitForVfsEventsExecuted(10, SECONDS) { }
          fileBasedIndex.changedFilesCollector.ensureUpToDate()

          val foreignFileId = (foreignFile as VirtualFileWithId).id
          val collector = fileBasedIndex.filesToUpdateCollector
          val projectAVisits = AtomicInteger()
          val projectBVisits = AtomicInteger()
          fileBasedIndex.installForceUpdateTestHook(testRootDisposable) { project, requests ->
            if (requests.any { it.fileId == foreignFileId }) {
              when (project) {
                projectA -> projectAVisits.incrementAndGet()
                projectB -> projectBVisits.incrementAndGet()
              }
            }
          }
          // Preserve the original owner only in request diagnostics, as happens when project membership changes before consumption.
          collector.scheduleForUpdate(FileIndexingRequest.updateRequest(foreignFile), setOf(projectB), emptyList())
          try {
            assertThat(collector.containsFileId(foreignFileId))
              .describedAs { "The controlled foreign request must be pending before forceUpdate" }
              .isTrue()

            forceUpdate(fileBasedIndex, projectA)
            forceUpdate(fileBasedIndex, projectA)
            forceUpdate(fileBasedIndex, projectB)

            assertThat(projectAVisits.get())
              .describedAs { "Repeated forceUpdate calls must examine the foreign request only once for the first project" }
              .isEqualTo(1)
            assertThat(projectBVisits.get())
              .describedAs { "The second project must examine the request from its own cursor once" }
              .isEqualTo(1)
          }
          finally {
            collector.removeScheduledFileFromUpdate(foreignFile)
          }
        }
      }
    }
  }

  /** Verifies that advancing one project cursor does not consume an indexable request owned by another project. */
  @Test
  fun `project processes request left pending by another project cursor`() {
    runBlocking {
      val filetype = FakeFileType()
      registerFiletype(filetype)
      openProject("${testNameRule.methodName}-A") { projectA, moduleA ->
        val srcA = tempDir.createVirtualDir("src-owned-A")
        moduleA.createContentRoot(projectA, srcA)
        IndexingTestUtil.waitUntilIndexesAreReady(projectA)

        openProject("${testNameRule.methodName}-B") { projectB, moduleB ->
          val srcB = tempDir.createVirtualDir("src-owned-B")
          moduleB.createContentRoot(projectB, srcB)
          IndexingTestUtil.waitUntilIndexesAreReady(projectB)

          val ownedFile = edtWriteAction { srcB.createFile("owned.${filetype.defaultExtension}") }
          IndexingTestUtil.waitUntilIndexesAreReady(projectB)
          val fileBasedIndex = FileBasedIndex.getInstance() as FileBasedIndexImpl
          waitForVfsEvents(fileBasedIndex)
          forceUpdate(fileBasedIndex, projectB)

          val collector = fileBasedIndex.filesToUpdateCollector
          val request = FileIndexingRequest.updateRequest(ownedFile)
          val projectAVisits = AtomicInteger()
          val projectBVisits = AtomicInteger()
          fileBasedIndex.installForceUpdateTestHook(testRootDisposable) { project, requests ->
            if (requests.any { it === request }) {
              when (project) {
                projectA -> projectAVisits.incrementAndGet()
                projectB -> projectBVisits.incrementAndGet()
              }
            }
          }
          collector.scheduleForUpdate(request, setOf(projectB), emptyList())

          forceUpdate(fileBasedIndex, projectA)
          assertThat(collector.isCurrent(request))
            .describedAs { "The first project's cursor must not consume a request belonging only to the second project" }
            .isTrue()

          forceUpdate(fileBasedIndex, projectB)
          assertThat(collector.isCurrent(request))
            .describedAs { "The owning project must process and remove its pending request" }
            .isFalse()
          assertThat(projectAVisits.get())
            .describedAs { "The first project must advance past the request after rejecting it by project scope" }
            .isEqualTo(1)
          assertThat(projectBVisits.get())
            .describedAs { "The owning project must independently visit the request from its own cursor" }
            .isEqualTo(1)
        }
      }
    }
  }

  /** Verifies that a newly registered project starts before requests published prior to its first cursor advancement. */
  @Test
  fun `new project observes request published before its registration`() {
    assumeProjectRequestCursorFeaturesEnabled()
    runBlocking {
      openProject("${testNameRule.methodName}-A") { projectA, moduleA ->
        val srcA = tempDir.createVirtualDir("src-new-project-A")
        moduleA.createContentRoot(projectA, srcA)
        IndexingTestUtil.waitUntilIndexesAreReady(projectA)

        val foreignFile = edtWriteAction { tempDir.createVirtualDir("outside-new-project").createFile("foreign.txt") }
        val fileBasedIndex = FileBasedIndex.getInstance() as FileBasedIndexImpl
        waitForVfsEvents(fileBasedIndex)
        forceUpdate(fileBasedIndex, projectA)
        val collector = fileBasedIndex.filesToUpdateCollector
        collector.removeScheduledFileFromUpdate(foreignFile)
        val request = FileIndexingRequest.updateRequest(foreignFile)
        collector.scheduleForUpdate(request, setOf(projectA), emptyList())
        try {
          val newProjectVisits = AtomicInteger()
          fileBasedIndex.installForceUpdateTestHook(testRootDisposable) { project, requests ->
            if (project != projectA && requests.any { it === request }) {
              newProjectVisits.incrementAndGet()
            }
          }

          openProject("${testNameRule.methodName}-B") { projectB, moduleB ->
            val srcB = tempDir.createVirtualDir("src-new-project-B")
            moduleB.createContentRoot(projectB, srcB)
            IndexingTestUtil.waitUntilIndexesAreReady(projectB)
            forceUpdate(fileBasedIndex, projectB)

            assertThat(newProjectVisits.get())
              .describedAs { "A new project must observe the pre-existing request exactly once before its cursor catches up" }
              .isEqualTo(1)
            assertThat(collector.isCurrent(request))
              .describedAs { "The request must remain until the older project also advances past it" }
              .isTrue()

            forceUpdate(fileBasedIndex, projectA)
          }
        }
        finally {
          collector.removeScheduledFileFromUpdate(foreignFile)
        }
      }
    }
  }

  /** Verifies that completing an older request cannot remove a replacement published after the processing snapshot. */
  @Test
  fun `rescheduled request remains pending after older request completes`() {
    runBlocking {
      val filetype = FakeFileType()
      registerFiletype(filetype)
      openProject(testNameRule.methodName) { project, module ->
        val src = tempDir.createVirtualDir("src-rescheduled")
        module.createContentRoot(project, src)
        IndexingTestUtil.waitUntilIndexesAreReady(project)
        val file = edtWriteAction { src.createFile("rescheduled.${filetype.defaultExtension}") }
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        val fileBasedIndex = FileBasedIndex.getInstance() as FileBasedIndexImpl
        waitForVfsEvents(fileBasedIndex)
        forceUpdate(fileBasedIndex, project)
        val collector = fileBasedIndex.filesToUpdateCollector
        val originalRequest = FileIndexingRequest.updateRequest(file)
        var replacementRequest: FileIndexingRequest? = null
        val observedRequests = mutableListOf<FileIndexingRequest>()
        fileBasedIndex.installForceUpdateTestHook(testRootDisposable) { observedProject, requests ->
          if (observedProject == project) {
            requests.firstOrNull { it === originalRequest || it === replacementRequest }?.let(observedRequests::add)
          }
          if (replacementRequest == null && requests.any { it === originalRequest }) {
            replacementRequest = FileIndexingRequest.updateRequest(file).also {
              collector.scheduleForUpdate(it, setOf(project), emptyList())
            }
          }
        }
        collector.scheduleForUpdate(originalRequest, setOf(project), emptyList())

        forceUpdate(fileBasedIndex, project)
        val replacement = checkNotNull(replacementRequest) { "The controlled processing window must publish a replacement request" }
        assertThat(collector.isCurrent(replacement))
          .describedAs { "Completion of the older request must leave its replacement pending" }
          .isTrue()

        forceUpdate(fileBasedIndex, project)
        assertThat(collector.isCurrent(replacement))
          .describedAs { "The next project update must process the replacement generation" }
          .isFalse()
        assertThat(observedRequests)
          .describedAs { "Successive cursor snapshots must expose exactly two request generations" }
          .hasSize(2)
        assertThat(observedRequests[0])
          .describedAs { "The first snapshot must expose the original request instance" }
          .isSameAs(originalRequest)
        assertThat(observedRequests[1])
          .describedAs { "The next snapshot must expose the replacement request instance" }
          .isSameAs(replacement)
      }
    }
  }

  /** Verifies that a request published after snapshot capture remains beyond the cursor committed for that snapshot. */
  @Test
  fun `request published while snapshot is processed is visited by next update`() {
    runBlocking {
      openProject(testNameRule.methodName) { project, _ ->
        val firstFile = edtWriteAction { tempDir.createVirtualDir("outside-snapshot").createFile("first.txt") }
        val secondFile = edtWriteAction { firstFile.parent.createFile("second.txt") }
        val fileBasedIndex = FileBasedIndex.getInstance() as FileBasedIndexImpl
        waitForVfsEvents(fileBasedIndex)
        forceUpdate(fileBasedIndex, project)

        val collector = fileBasedIndex.filesToUpdateCollector
        collector.removeScheduledFileFromUpdate(firstFile)
        collector.removeScheduledFileFromUpdate(secondFile)
        val firstRequest = FileIndexingRequest.updateRequest(firstFile)
        var secondRequest: FileIndexingRequest? = null
        val observedBatches = mutableListOf<List<FileIndexingRequest>>()
        fileBasedIndex.installForceUpdateTestHook(testRootDisposable) { observedProject, requests ->
          if (observedProject != project) return@installForceUpdateTestHook
          val controlledRequests = requests.filter { it === firstRequest || it === secondRequest }
          if (controlledRequests.isNotEmpty()) {
            observedBatches += controlledRequests
          }
          if (secondRequest == null && requests.any { it === firstRequest }) {
            secondRequest = FileIndexingRequest.updateRequest(secondFile).also {
              collector.scheduleForUpdate(it, setOf(project), emptyList())
            }
          }
        }
        collector.scheduleForUpdate(firstRequest, setOf(project), emptyList())
        try {
          forceUpdate(fileBasedIndex, project)
          val publishedAfterSnapshot = checkNotNull(secondRequest) { "The first snapshot must publish a later request" }
          forceUpdate(fileBasedIndex, project)

          val filterByRequestVersion = SystemProperties.getBooleanProperty(
            "FileBasedIndexImpl.USE_REQUEST_VERSION_TO_SKIP_REPEATING_UPDATES",
            false,
          )
          val cleanupVisitedRequests = SystemProperties.getBooleanProperty(
            "FileBasedIndexImpl.CLEAN_REQUESTS_VISITED_BY_ALL_PROJECTS",
            true,
          )
          val expectedSecondBatch = if (filterByRequestVersion || cleanupVisitedRequests) {
            listOf(publishedAfterSnapshot)
          }
          else {
            listOf(firstRequest, publishedAfterSnapshot)
          }
          assertThat(observedBatches)
            .describedAs { "The project cursor must retain requests published after the captured boundary" }
            .hasSize(2)
          assertThat(observedBatches[0])
            .describedAs { "The first update must expose the request published before its boundary" }
            .containsExactly(firstRequest)
          assertThat(observedBatches[1])
            .describedAs { "The next update must apply the configured request retention rules" }
            .containsExactlyInAnyOrderElementsOf(expectedSecondBatch)
        }
        finally {
          collector.removeScheduledFileFromUpdate(firstFile)
          collector.removeScheduledFileFromUpdate(secondFile)
        }
      }
    }
  }

  /** Verifies that an aborted update retains its request and a later successful pass retries the same cursor suffix. */
  @Test
  fun `failed request is retried before project cursor advances`() {
    assumeProjectRequestCursorFeaturesEnabled()
    runBlocking {
      openProject(testNameRule.methodName) { project, _ ->
        val file = edtWriteAction { tempDir.createVirtualDir("outside-retry").createFile("retry.txt") }
        val fileBasedIndex = FileBasedIndex.getInstance() as FileBasedIndexImpl
        waitForVfsEvents(fileBasedIndex)
        forceUpdate(fileBasedIndex, project)

        val collector = fileBasedIndex.filesToUpdateCollector
        collector.removeScheduledFileFromUpdate(file)
        val request = FileIndexingRequest.updateRequest(file)
        val visits = AtomicInteger()
        var failFirstAttempt = true
        fileBasedIndex.installForceUpdateTestHook(testRootDisposable) { observedProject, requests ->
          if (observedProject == project && requests.any { it === request }) {
            visits.incrementAndGet()
            if (failFirstAttempt) {
              failFirstAttempt = false
              error("Controlled forceUpdate failure before request completion")
            }
          }
        }
        collector.scheduleForUpdate(request, setOf(project), emptyList())

        try {
          val firstFailure = runCatching { forceUpdate(fileBasedIndex, project) }.exceptionOrNull()
          assertThat(firstFailure)
            .describedAs { "The controlled first update must abort before committing its cursor" }
            .isInstanceOf(IllegalStateException::class.java)
          assertThat(collector.isCurrent(request))
            .describedAs { "An aborted update must retain the request for a retry" }
            .isTrue()

          forceUpdate(fileBasedIndex, project)
          assertThat(visits.get())
            .describedAs { "The same cursor suffix must be visited once for the failed attempt and once for its retry" }
            .isEqualTo(2)

          forceUpdate(fileBasedIndex, project)
          assertThat(visits.get())
            .describedAs { "A successful retry must advance the project cursor past the retried request" }
            .isEqualTo(2)
        }
        finally {
          collector.removeScheduledFileFromUpdate(file)
        }
      }
    }
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

  /** Runs the unrestricted synchronous update path used to verify project cursor advancement. */
  private suspend fun forceUpdate(fileBasedIndex: FileBasedIndexImpl, project: Project) {
    smartReadAction(project) {
      fileBasedIndex.forceUpdateProjectInTest(project)
    }
  }

  /** Skips cursor-cleanup contract tests when either independently configurable production feature is disabled. */
  private fun assumeProjectRequestCursorFeaturesEnabled() {
    assumeTrue(
      "Project request cursor tracking is disabled",
      FileBasedIndexImpl.USE_REQUEST_VERSION_TO_SKIP_REPEATING_UPDATES
    )
    assumeTrue(
      "Cleanup by project request cursors is disabled",
      FileBasedIndexImpl.CLEAN_REQUESTS_VISITED_BY_ALL_PROJECTS
    )
  }

  /** Delivers queued VFS events into the request collector before a test installs controlled requests. */
  private fun waitForVfsEvents(fileBasedIndex: FileBasedIndexImpl) {
    fileBasedIndex.changedFilesCollector.waitForVfsEventsExecuted(10, SECONDS) { }
    fileBasedIndex.changedFilesCollector.ensureUpToDate()
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

  /** Verifies that a restricted update does not advance the project cursor. */
  @Test
  fun `restricted update leaves cursor for unrestricted pass`() {
    runBlocking {
      val filetype = FakeFileType()
      registerFiletype(filetype)
      openProject(testNameRule.methodName) { project, module ->
        val src = tempDir.createVirtualDir("src-restricted-update")
        module.createContentRoot(project, src)
        IndexingTestUtil.waitUntilIndexesAreReady(project)
        val restrictedFile = edtWriteAction { src.createFile("restricted.${filetype.defaultExtension}") }
        val pendingFile = edtWriteAction { src.createFile("pending.${filetype.defaultExtension}") }
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        val fileBasedIndex = FileBasedIndex.getInstance() as FileBasedIndexImpl
        waitForVfsEvents(fileBasedIndex)
        forceUpdate(fileBasedIndex, project)
        val collector = fileBasedIndex.filesToUpdateCollector
        val request = FileIndexingRequest.updateRequest(pendingFile)
        val visits = AtomicInteger()
        fileBasedIndex.installForceUpdateTestHook(testRootDisposable) { observedProject, requests ->
          if (observedProject == project && requests.any { it === request }) {
            visits.incrementAndGet()
          }
        }
        collector.scheduleForUpdate(request, setOf(project), emptyList())

        smartReadAction(project) {
          fileBasedIndex.ensureUpToDate(IdIndex.NAME, project, GlobalSearchScope.everythingScope(project), restrictedFile)
        }
        assertThat(collector.isCurrent(request))
          .describedAs { "The restricted update must leave the other request pending" }
          .isTrue()

        forceUpdate(fileBasedIndex, project)
        assertThat(visits.get())
          .describedAs { "The unrestricted pass must revisit a request seen by the restricted pass" }
          .isEqualTo(2)
        assertThat(collector.isCurrent(request))
          .describedAs { "The unrestricted pass must complete the pending request" }
          .isFalse()
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

    workspaceModel.update("creating test module") { storage ->
      storage addEntity ModuleEntity(
        name = TEST_MODULE_NAME,
        dependencies = emptyList(),
        entitySource = TestModuleEntitySource
      )
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
