// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.closeProjectAsync
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.createTestOpenProjectOptions
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.registerExtension
import com.intellij.util.application
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import com.intellij.workspaceModel.ide.impl.WorkspaceModelCacheImpl
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayList
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.swing.Icon
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Exercises the externally visible indexing contract while projects and VFS files change concurrently.
 * The scenario intentionally knows nothing about request versions or project cursors.
 */
@TestApplication
internal class MultiProjectIndexingContractTest {
  companion object {
    private const val FILES_TO_CHANGE: Int = 500
    private const val TURNS = 16

    private const val MODULE_NAME: String = "indexing-contract"
    private const val SEED_PROPERTY: String = "indexing.contract.seed"

    private val indexFixture = testFixture("multi-project version-token index") {
      val disposable = Disposer.newDisposable("MultiProjectIndexingContractTest index")
      val index = VersionTokenIndex()
      withContext(Dispatchers.EDT) {
        val tumbler = FileBasedIndexTumbler("register multi-project version-token index")
        tumbler.turnOff()
        try {
          val fileTypeManager = FileTypeManager.getInstance() as FileTypeManagerImpl
          val corePlugin = PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID)!!
          fileTypeManager.registerFileType(
            index.fileType,
            listOf(ExtensionFileNameMatcher(index.fileType.defaultExtension)),
            disposable,
            corePlugin,
          )
          application.registerExtension(FileBasedIndexExtension.EXTENSION_POINT_NAME, index, disposable)
        }
        finally {
          tumbler.turnOn()
        }
      }
      initialized(index) {
        index.gate.release()
        withContext(Dispatchers.EDT) {
          val tumbler = FileBasedIndexTumbler("unregister multi-project version-token index")
          tumbler.turnOff()
          try {
            Disposer.dispose(disposable)
          }
          finally {
            tumbler.turnOn()
          }
        }
      }
    }
  }

  @TempDir
  lateinit var tempDir: Path

  @TestDisposable
  lateinit var testDisposable: Disposable

  private val openProjects = mutableListOf<TestProject>()

  /** Releases a blocked mapper and closes every project even if a scenario fails halfway through. */
  @AfterEach
  fun tearDown(): Unit = timeoutRunBlocking {
    indexFixture.get().gate.release()
    openProjects.toList().asReversed().forEach { handle ->
      if (!handle.project.isDisposed) {
        handle.project.closeProjectAsync(save = false)
      }
    }
    openProjects.clear()
  }

  /** Verifies final index state after independent workers race through the global write-action order */
  @Test
  @Timeout(600)
  fun `with many parallel competing writes only the latest version stays in index`(): Unit = timeoutRunBlocking(timeout = 590.seconds) {
    val indexToTest = indexFixture.get()
    val scenarioModel = ScenarioModel()
    val projectsToTest = listOf(openProject("A"), openProject("B"), openProject("C"))
    val filesByProject = projectsToTest.associateWith { project ->
      (0 until FILES_TO_CHANGE).map { fileNo ->
        createFile(project, scenarioModel, "tree-${fileNo % 3}/file-$fileNo", generation = 1)
      }
    }
    awaitIndexesReady(*projectsToTest.toTypedArray())
    assertIndexMatchesModel(indexToTest, scenarioModel, *projectsToTest.toTypedArray())

    for (i in 1..TURNS) {
      filesByProject.values.flatten().map { file ->
        async(Dispatchers.Default) {
          repeat(4) {
            edtWriteAction {
              file.updateContentWithGeneration(scenarioModel, scenarioModel.nextGeneration(file))
            }
          }
        }
      }.awaitAll()

      awaitIndexesReady(*projectsToTest.toTypedArray())
      assertIndexMatchesModel(indexToTest, scenarioModel, *projectsToTest.toTypedArray())

      assertEquals(0, PendingRequestsProbe.pendingCount(), "Concurrent writers must not leave requests pending")
    }
  }

  @ParameterizedTest(name = "seed={0}")
  @ValueSource(longs = [2966L, 0x5EEDL, 0xC0FFEEL])
  @Timeout(600)
  fun `with many parallel writes for many projects closing and opening still only the latest version stays in index`(declaredSeed: Long): Unit =
    timeoutRunBlocking(timeout = 590.seconds) {
      val seed = System.getProperty(SEED_PROPERTY)?.toLong() ?: declaredSeed
      val indexToTest = indexFixture.get()
      val scenarioModel = ScenarioModel(seed)
      val rnd = Random(seed)
      val projectIds = listOf("A", "B", "C")
      val projectIdsToReopen = listOf("A", "C")
      try {
        val openProjectsById = projectIds.associateWithTo(linkedMapOf()) { id ->
          openProject(id).also { scenarioModel.recordCommand("open $id") }
        }
        val scenarioFiles = openProjectsById.values.flatMap { project ->
          (0 until FILES_TO_CHANGE).map { fileNo -> createFile(project, scenarioModel, "tree-${fileNo % 2}/file-$fileNo", generation = 1) }
        }
        awaitIndexesReady(*openProjectsById.values.toTypedArray())
        assertIndexMatchesModel(indexToTest, scenarioModel, *openProjectsById.values.toTypedArray())

        repeat(TURNS) { turn ->
          scenarioModel.recordCommand("turn ${turn + 1}")
          val commandsByWorker = List(4) { worker ->
            val filesOwnedByWorker = scenarioFiles.filterIndexed { index, _ -> index % 4 == worker }
            rnd.sample(samplesCount = FILES_TO_CHANGE, chooseOutOf = filesOwnedByWorker)
              .map { file -> GeneratedWrite(worker, file) }
          }
          val lifecycleProjectIds = rnd.sample(samplesCount = 8, chooseOutOf = projectIdsToReopen)

          val writerTasks = commandsByWorker.map { commands ->
            async(Dispatchers.Default) {
              commands.forEach { command ->
                edtWriteAction {
                  val generation = scenarioModel.nextGeneration(command.file)
                  command.file.updateContentWithGeneration(scenarioModel, generation)
                  scenarioModel.recordCommand(
                    "worker-${command.worker} write ${command.file.projectId}/${command.file.logicalPath} v$generation"
                  )
                }
                yield()
              }
            }
          }
          lifecycleProjectIds.forEach { projectId ->
            val projectToClose = openProjectsById.remove(projectId)
            if (projectToClose == null) {
              openProjectsById[projectId] = openProject(projectId)
              scenarioModel.recordCommand("reopen $projectId")
            }
            else {
              closeProject(projectToClose)
              scenarioModel.recordCommand("close $projectId")
            }
            yield()
          }
          writerTasks.awaitAll()

          projectIdsToReopen.forEach { projectId ->
            if (projectId !in openProjectsById) {
              openProjectsById[projectId] = openProject(projectId)
              scenarioModel.recordCommand("final reopen $projectId")
            }
          }
          val finalOpenProjects = openProjectsById.values.toList()
          awaitIndexesReady(*finalOpenProjects.toTypedArray())
          val drainTargets = finalOpenProjects.map { project ->
            project to scenarioFiles.first { it.projectId == project.id }
          }
          drainPendingRequests(indexToTest, scenarioModel, *drainTargets.toTypedArray())
          assertEquals(
            0,
            PendingRequestsProbe.pendingCount(),
            "Turn ${turn + 1} must drain all pending requests. ${scenarioModel.diagnosticContext()}",
          )
        }

        // Checking every historical token once keeps TURNS linear while still detecting stale data from any turn.
        assertIndexMatchesModel(indexToTest, scenarioModel, *openProjectsById.values.toTypedArray())
      }
      catch (failure: Throwable) {
        throw AssertionError("Generated indexing workload failed. ${scenarioModel.diagnosticContext()}", failure)
      }
    }

  /** Verifies that closing the owner of published updates does not leave them permanently pending. */
  @Test
  @Timeout(60)
  fun `closing project with pending updates eventually drains foreign requests`(): Unit = timeoutRunBlocking(timeout = 55.seconds) {
    val indexToTest = indexFixture.get()
    val scenarioModel = ScenarioModel()
    val projectA = openProject("A")
    val projectB = openProject("B")

    val filesInProjectA = (0 until FILES_TO_CHANGE).map { fileNo ->
      createFile(projectA, scenarioModel, "tree-${fileNo % 5}/file-$fileNo", generation = 1)
    }
    val sentinelFileInProjectB = createFile(projectB, scenarioModel, "sentinel", generation = 1)
    awaitIndexesReady(projectA, projectB)
    assertIndexMatchesModel(indexToTest, scenarioModel, projectA, projectB)
    drainPendingRequests(indexToTest, scenarioModel, projectB to sentinelFileInProjectB)

    edtWriteAction {
      filesInProjectA.forEach { file ->
        file.updateContentWithGeneration(scenarioModel, generation = 2)
      }
      sentinelFileInProjectB.updateContentWithGeneration(scenarioModel, generation = 2)
    }
    val closingOfProjectA = readAction {
      assertEquals(
        setOf(sentinelFileInProjectB.virtualFile),
        queryInsideReadAction(indexToTest, projectB, scenarioModel.currentToken(sentinelFileInProjectB)),
        "Project B lookup must publish the global backlog before project A closes",
      )
      // Queue project closing before this read action ends, so it wins over background indexing of A.
      async(start = CoroutineStart.UNDISPATCHED) {
        closeProject(projectA, save = false)
      }
    }
    closingOfProjectA.await()

    awaitPendingRequestsDrained()
    assertIndexMatchesModel(indexToTest, scenarioModel, projectB)
    assertEquals(0, PendingRequestsProbe.pendingCount(),
                 "Requests from the closed project (now: foreign) must eventually be retired -- one way or another")
  }

  /** A change in a file with no owning project open -- is not lost, and accounted for in index when an owning project reopens */
  @Test
  @Timeout(600)
  fun `change while project is closed is visible after project reopen`(): Unit = timeoutRunBlocking(timeout = 590.seconds) {
    val indexToTest = indexFixture.get()
    val scenarioModel = ScenarioModel()
    var projectA = openProject("A")
    val projectB = openProject("B")
    val fileInProjectA = createFile(projectA, scenarioModel, "nested/file-A", generation = 1)
    val sentinelFileInProjectB = createFile(projectB, scenarioModel, "sentinel", generation = 1)
    awaitIndexesReady(projectA, projectB)
    assertIndexMatchesModel(indexToTest, scenarioModel, projectA, projectB)

    repeat(TURNS) { turn ->
      closeProject(projectA)
      edtWriteAction {
        fileInProjectA.updateContentWithGeneration(scenarioModel, scenarioModel.nextGeneration(fileInProjectA))
        sentinelFileInProjectB.updateContentWithGeneration(scenarioModel, scenarioModel.nextGeneration(sentinelFileInProjectB))
      }
      assertEquals(
        setOf(sentinelFileInProjectB.virtualFile),
        query(indexToTest, projectB, scenarioModel.currentToken(sentinelFileInProjectB)),
        "Open project B must keep progressing while project A is closed on turn ${turn + 1}",
      )
      drainPendingRequests(indexToTest, scenarioModel, projectB to sentinelFileInProjectB)
      assertEquals(
        0,
        PendingRequestsProbe.pendingCount(),
        "Project B must retire all globally pending requests before A reopens on turn ${turn + 1}",
      )

      projectA = openProject("A")
      awaitIndexesReady(projectA, projectB)
      assertIndexMatchesModel(indexToTest, scenarioModel, projectA, projectB)
      drainPendingRequests(
        indexToTest,
        scenarioModel,
        projectA to fileInProjectA,
        projectB to sentinelFileInProjectB,
      )
      assertEquals(
        0,
        PendingRequestsProbe.pendingCount(),
        "Reopen and sentinel processing must leave no pending requests on turn ${turn + 1}",
      )
    }
  }

  /** Verifies that a newer write into a same file wins after an older mapper was already in progress. */
  @Test
  @Timeout(60)
  fun `same-file replacement exposes only the newest token`(): Unit = timeoutRunBlocking(timeout = 55.seconds) {
    val indexToTest = indexFixture.get()
    val scenarioModel = ScenarioModel()
    val projectA = openProject("A")
    val fileInProjectA = createFile(projectA, scenarioModel, "same-file", generation = 1)
    awaitIndexesReady(projectA)
    assertIndexMatchesModel(indexToTest, scenarioModel, projectA)

    val oldVersionToken = "pA-fsame-file-v2"
    indexToTest.gate.arm(oldVersionToken)
    edtWriteAction { fileInProjectA.updateContentWithGeneration(scenarioModel, generation = 2) }
    val indexingOfOldVersion = async(Dispatchers.Default) { query(indexToTest, projectA, oldVersionToken) }
    indexToTest.gate.awaitEntered()

    val updateWithNewVersionStarted = CompletableDeferred<Unit>()
    val updateWithNewVersion = async(Dispatchers.Default) {
      updateWithNewVersionStarted.complete(Unit)
      edtWriteAction { fileInProjectA.updateContentWithGeneration(scenarioModel, generation = 3) }
    }
    updateWithNewVersionStarted.await()
    yield()
    indexToTest.gate.release()
    indexingOfOldVersion.await()
    updateWithNewVersion.await()

    awaitIndexesReady(projectA)
    assertIndexMatchesModel(indexToTest, scenarioModel, projectA)
    assertEquals(
      emptySet<VirtualFile>(),
      query(indexToTest, projectA, oldVersionToken),
      "The mapper's older token must not survive the newer write",
    )
  }

  /** Verifies that deleting and re-creating the file under same logical path leave only the new file's data in the index */
  @Test
  @Timeout(600)
  fun `delete and recreate removes stale index data`(): Unit = timeoutRunBlocking(timeout = 590.seconds) {
    val indexToTest = indexFixture.get()
    val scenarioModel = ScenarioModel()
    val projectA = openProject("A")
    var fileToReplace = createFile(projectA, scenarioModel, "replaceable", generation = 1)
    awaitIndexesReady(projectA)
    assertIndexMatchesModel(indexToTest, scenarioModel, projectA)

    repeat(TURNS) { turn ->
      edtWriteAction {
        fileToReplace.virtualFile.delete(this)
        scenarioModel.recordDelete(fileToReplace)
      }
      fileToReplace = createFile(projectA, scenarioModel, "replaceable", generation = turn + 2)
      awaitIndexesReady(projectA)
      assertIndexMatchesModel(indexToTest, scenarioModel, projectA)
      assertEquals(
        setOf(fileToReplace.virtualFile),
        query(indexToTest, projectA, scenarioModel.currentToken(fileToReplace)),
        "Turn ${turn + 1} must expose only the latest VFS identity at the recreated path",
      )
    }
  }

  /** Verifies that indexer's failure does not prevent a later version from becoming current */
  @Test
  @Timeout(60)
  fun `mapper failure is recovered by the next indexing opportunity`(): Unit = timeoutRunBlocking(timeout = 55.seconds) {
    val indexToTest = indexFixture.get()
    val scenarioModel = ScenarioModel()
    val projectA = openProject("A")
    val fileInProjectA = createFile(projectA, scenarioModel, "retry", generation = 1)
    awaitIndexesReady(projectA)
    assertIndexMatchesModel(indexToTest, scenarioModel, projectA)

    edtWriteAction { fileInProjectA.updateContentWithGeneration(scenarioModel, generation = 2) }
    val tokenToFail = scenarioModel.currentToken(fileInProjectA)
    indexToTest.failOnce(tokenToFail)
    LoggedErrorProcessor.executeWith(ExpectedMapperFailureProcessor()).use {
      query(indexToTest, projectA, tokenToFail)
    }

    edtWriteAction { fileInProjectA.updateContentWithGeneration(scenarioModel, generation = 3) }
    awaitIndexesReady(projectA)
    assertIndexMatchesModel(indexToTest, scenarioModel, projectA)
  }


  /** Opens a real non-light project and gives it one persisted module/content root. */
  private suspend fun openProject(id: String): TestProject {
    val projectPath = tempDir.resolve("project-$id")
    Files.createDirectories(projectPath)
    WorkspaceModelCacheImpl.forceEnableCaching(testDisposable)
    @Suppress("DATA_CLASS_INVISIBLE_COPY_USAGE_WARNING", "DEPRECATION")
    val options = createTestOpenProjectOptions().copy(projectName = "indexing-contract-$id")
    val project = checkNotNull(ProjectUtil.openOrImportAsync(projectPath, options)) { "Cannot open project $id" }
    val module = ModuleManager.getInstance(project).findModuleByName(MODULE_NAME) ?: createModule(project)
    val sourceRoot = edtWriteAction {
      val projectRoot = checkNotNull(VfsUtil.findFile(projectPath, true))
      projectRoot.findChild("src") ?: projectRoot.createChildDirectory(this, "src")
    }
    if (sourceRoot !in ModuleRootManager.getInstance(module).contentRoots) {
      ModuleRootModificationUtil.addContentRoot(module, sourceRoot)
    }
    return TestProject(id, project, sourceRoot).also(openProjects::add)
  }

  /** Closes one managed project, optionally persisting its model for a later reopen. */
  private suspend fun closeProject(handle: TestProject, save: Boolean = true) {
    handle.project.closeProjectAsync(save)
    openProjects.remove(handle)
  }

  /** Creates the minimal module needed to make a project root indexable. */
  private suspend fun createModule(project: Project): Module = edtWriteAction {
    val imlPath = checkNotNull(project.basePath).let(Path::of).resolve("$MODULE_NAME.iml")
    ModuleManager.getInstance(project).newModule(imlPath, "EMPTY_MODULE")
  }

  /** Creates a logical file and updates the oracle at the same VFS linearization point. */
  private suspend fun createFile(
    project: TestProject,
    scenarioModel: ScenarioModel,
    logicalPath: String,
    generation: Int,
  ): ScenarioFile = edtWriteAction {
    val parentPath = logicalPath.substringBeforeLast('/', "")
    var parent = project.sourceRoot
    if (parentPath.isNotEmpty()) {
      parentPath.split('/').forEach { name ->
        parent = parent.findChild(name) ?: parent.createChildDirectory(this, name)
      }
    }
    val logicalId = logicalPath.substringAfterLast('/')
    val virtualFile = parent.createChildData(this, "$logicalId.${VersionTokenFileType.EXTENSION}")
    val scenarioFile = ScenarioFile(project.id, logicalPath, virtualFile)
    scenarioFile.updateContentWithGeneration(scenarioModel, generation)
    scenarioFile
  }

  /** Writes one generation and records exactly the state made visible by that write action. */
  private fun ScenarioFile.updateContentWithGeneration(scenarioModel: ScenarioModel, generation: Int) {
    val token = "p${this.projectId}-f${this.logicalPath.replace('/', '_')}-v$generation"
    this.virtualFile.setBinaryContent(token.toByteArray())
    scenarioModel.recordWrite(this, generation, token)
  }

  /** Waits for the ordinary project indexing lifecycle, without invoking implementation cleanup hooks. */
  private suspend fun awaitIndexesReady(vararg projects: TestProject) {
    projects.forEach { IndexingTestUtil.suspendUntilIndexesAreReady(it.project) }
  }

  /** Reads the one test index through its public inverted lookup API. */
  private suspend fun query(indexToTest: VersionTokenIndex, project: TestProject, token: String): Set<VirtualFile> = readAction {
    queryInsideReadAction(indexToTest, project, token)
  }

  /** Performs the public lookup when the caller must coordinate work before releasing its read action. */
  private fun queryInsideReadAction(indexToTest: VersionTokenIndex, project: TestProject, token: String): Set<VirtualFile> {
    val scope = GlobalSearchScope.projectScope(project.project)
    FileBasedIndex.getInstance().ensureUpToDate(indexToTest.name, project.project, scope)
    return FileBasedIndex.getInstance().getContainingFiles(indexToTest.name, token, scope).toSet()
  }

  /** Checks every generated token, so both freshness and removal of stale inverted data are covered. */
  private suspend fun assertIndexMatchesModel(
    indexToTest: VersionTokenIndex,
    scenarioModel: ScenarioModel,
    vararg projects: TestProject,
  ) {
    projects.forEach { project ->
      scenarioModel.currentTokenExpectations(project.id).forEach { (token, expected) ->
        val actual = query(indexToTest, project, token)
        assertEquals(
          expected,
          actual,
          "Current token '$token' must be visible after quiescence; " +
          "pending=${PendingRequestsProbe.pendingCount()}. ${scenarioModel.diagnosticContext()}",
        )
      }
      scenarioModel.allTokens().forEach { token ->
        val expected = scenarioModel.expectedFiles(project.id, token)
        val actual = query(indexToTest, project, token)
        assertEquals(
          expected,
          actual,
          "Token '$token' must expose exactly the current files in project ${project.id}; " +
          "pending=${PendingRequestsProbe.pendingCount()}. ${scenarioModel.diagnosticContext()}",
        )
      }
    }
  }

  /** Creates one ordinary indexing opportunity, then waits until the resulting global work is retired. */
  private suspend fun drainPendingRequests(
    indexToTest: VersionTokenIndex,
    scenarioModel: ScenarioModel,
    vararg targets: Pair<TestProject, ScenarioFile>,
  ) {
    withTimeout(20.seconds) {
      if (PendingRequestsProbe.pendingCount() == 0) return@withTimeout

      edtWriteAction {
        targets.forEach { (_, sentinelFile) ->
          sentinelFile.updateContentWithGeneration(scenarioModel, scenarioModel.nextGeneration(sentinelFile))
        }
      }
      while (PendingRequestsProbe.pendingCount() != 0) {
        targets.forEach { (sentinelProject, sentinelFile) ->
          val token = scenarioModel.currentToken(sentinelFile)
          assertEquals(
            setOf(sentinelFile.virtualFile),
            query(indexToTest, sentinelProject, token),
            "The drain opportunity must index the newest sentinel. ${scenarioModel.diagnosticContext()}",
          )
        }
        awaitIndexesReady(*targets.map { it.first }.toTypedArray())
        delay(10.milliseconds)
      }
    }
  }

  /** Waits for lifecycle-driven cleanup without creating additional indexing work. */
  private suspend fun awaitPendingRequestsDrained() {
    val drained = withTimeoutOrNull(20.seconds) {
      while (PendingRequestsProbe.pendingCount() != 0) {
        delay(10.milliseconds)
      }
      true
    }
    assertEquals(
      true,
      drained,
      "Pending requests must be retired without additional writes or lookups; pending=${PendingRequestsProbe.pendingCount()}",
    )
  }
}

/** @return [samplesCount] items randomly chosen from [chooseOutOf] */
private fun <T> Random.sample(samplesCount: Int, chooseOutOf: List<T>): List<T> =
  List(samplesCount) { chooseOutOf[this.nextInt(chooseOutOf.size)] }

/** Holds the real project objects separately from the scenario's implementation-independent data model. */
private data class TestProject(val id: String, val project: Project, val sourceRoot: VirtualFile)

/** Identifies a file across content generations without relying on VFS ids. */
private data class ScenarioFile(val projectId: String, val logicalPath: String, val virtualFile: VirtualFile)

/** One generated writer command, prepared before workers start so the seed fully defines the workload. */
private data class GeneratedWrite(val worker: Int, val file: ScenarioFile)

/** Stores the expected latest generation and every token needed to detect stale inverted data. */
private class ScenarioModel(private val seed: Long? = null) {
  private data class FileState(val generation: Int, val token: String, val exists: Boolean)

  private val states = LinkedHashMap<ScenarioFile, FileState>()
  private val allTokens = LinkedHashSet<String>()
  private val commandLog = ArrayList<String>()

  /** Records a successful VFS write while its enclosing write action still defines the global order. */
  @Synchronized
  fun recordWrite(file: ScenarioFile, generation: Int, token: String) {
    states[file] = FileState(generation, token, exists = true)
    allTokens += token
  }

  /** Marks the latest state absent while retaining its token for stale-data checks. */
  @Synchronized
  fun recordDelete(file: ScenarioFile) {
    val current = checkNotNull(states[file])
    states[file] = current.copy(exists = false)
  }

  /** Appends an observable scenario action in its actual completion order. */
  @Synchronized
  fun recordCommand(command: String) {
    commandLog += command
  }

  /** Returns the generation following the latest write of one logical file. */
  @Synchronized
  fun nextGeneration(file: ScenarioFile): Int = checkNotNull(states[file]).generation + 1

  /** Returns the token that must be visible after the latest successful write. */
  @Synchronized
  fun currentToken(file: ScenarioFile): String = checkNotNull(states[file]).token

  /** Returns a stable token snapshot for exhaustive inverted-index checks. */
  @Synchronized
  fun allTokens(): Set<String> = allTokens.toSet()

  /** Returns current project tokens first, establishing the demand-driven quiescence boundary before stale checks. */
  @Synchronized
  fun currentTokenExpectations(projectId: String): Map<String, Set<VirtualFile>> = states
    .filter { (file, state) -> file.projectId == projectId && state.exists }
    .entries
    .groupBy(keySelector = { it.value.token }, valueTransform = { it.key.virtualFile })
    .mapValues { (_, files) -> files.toSet() }

  /** Computes expected files without consulting the index or any pending-request representation. */
  @Synchronized
  fun expectedFiles(projectId: String, token: String): Set<VirtualFile> = states
    .filter { (file, state) -> file.projectId == projectId && state.exists && state.token == token }
    .keys
    .mapTo(mutableSetOf(), ScenarioFile::virtualFile)

  /** Provides the seed and linearized command history needed to reproduce a generated failure. */
  @Synchronized
  fun diagnosticContext(): String = if (seed == null) {
    "deterministic scenario"
  }
  else {
    "seed=$seed, commands=${commandLog.joinToString(separator = "; ")}"
  }
}

/** Isolates the sole structural assertion so a future pending store changes only this adapter. */
private object PendingRequestsProbe {
  /** @return # of indexing requests currently pending */
  fun pendingCount(): Int = (FileBasedIndex.getInstance() as FileBasedIndexImpl)
    .filesToUpdateCollector
    .filesToUpdate
    .size
}


/**
 * Very simple content-dependent index: maps `file => mapOf( file.content to null )`
 * (Content is used in the role of 'version-token', hence the name)
 * Implementation allows to:
 * - set a specific content [failOnce] on which indexer throws exception
 * - make indexer wait on specific tokens -- by configuring [gate]
 */
private class VersionTokenIndex : ScalarIndexExtension<String>() {
  val fileType = VersionTokenFileType()

  val gate = WaitingGate()
  private val tokenToFail = AtomicReference<String?>()

  /** Arranges exactly one mapper failure for [token] without changing normal indexing behavior afterward. */
  fun failOnce(token: String) {
    check(tokenToFail.compareAndSet(null, token)) { "A mapper failure is already armed" }
  }

  override fun getName(): ID<String, Void> = NAME
  override fun getVersion(): Int = 1
  override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE
  override fun getInputFilter(): FileBasedIndex.InputFilter = FileBasedIndex.InputFilter { it.fileType == fileType }
  override fun dependsOnFileContent(): Boolean = true

  /** Maps the file content to one 'version token', with some spices around */
  override fun getIndexer(): DataIndexer<String, Void, FileContent> = DataIndexer { content ->
    val token = content.contentAsText.toString()

    gate.intercept(token)

    if (tokenToFail.compareAndSet(token, null)) {
      error("$EXPECTED_FAILURE_MARKER: $token")
    }

    mapOf(token to null)
  }

  private companion object {
    val NAME: ID<String, Void> = ID.create("indexing.test.multi.project.version.tokens")
  }

  /** Handle to block selected indexer calls */
  class WaitingGate {
    @Volatile
    private var state: State? = null

    /** Arms the gate for every token beginning with [tokenPrefix]. */
    fun arm(tokenPrefix: String) {
      check(state == null) { "Mapper gate is already armed" }
      state = State(tokenPrefix)
    }

    /** Blocks matching mapper calls until the scenario permits them to complete. */
    fun intercept(token: String) {
      val current = state ?: return
      if (!token.startsWith(current.tokenPrefix)) return
      current.entered.countDown()
      check(current.release.await(20, TimeUnit.SECONDS)) { "Timed out waiting to release mapper for $token" }
    }

    /** Waits until at least one selected mapper is demonstrably in progress. */
    suspend fun awaitEntered() {
      val current = checkNotNull(state) { "Mapper gate is not armed" }
      val entered = withContext(Dispatchers.IO) { current.entered.await(20, TimeUnit.SECONDS) }
      assertTrue(entered, "A selected mapper must start before the project is closed")
    }

    /** Releases all matching mapper calls and makes the gate reusable by the next test. */
    fun release() {
      state?.release?.countDown()
      state = null
    }

    private class State(val tokenPrefix: String) {
      val entered = CountDownLatch(1)
      val release = CountDownLatch(1)
    }
  }
}

/** Suppresses only the deliberately injected mapper error while preserving every unrelated logged failure. */
private class ExpectedMapperFailureProcessor : LoggedErrorProcessor() {
  override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?): Set<Action> {
    return if (generateSequence(t) { it.cause }.any { it.message?.contains(EXPECTED_FAILURE_MARKER) == true }) {
      emptySet()
    }
    else {
      super.processError(category, message, details, t)
    }
  }
}

private const val EXPECTED_FAILURE_MARKER: String = "expected version-token mapper failure"

/** Restricts the test index to files owned by this fixture. */
private class VersionTokenFileType : FileType {
  override fun getName(): String = "MultiProjectVersionToken"
  override fun getDescription(): String = "Multi-project indexing version-token test file"
  override fun getDefaultExtension(): String = EXTENSION
  override fun getIcon(): Icon? = null
  override fun isBinary(): Boolean = false

  companion object {
    const val EXTENSION: String = "version-token"
  }
}
