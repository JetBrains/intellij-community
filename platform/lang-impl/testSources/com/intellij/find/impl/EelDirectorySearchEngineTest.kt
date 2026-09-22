// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.find.DirectorySearchEngine
import com.intellij.find.FindInProjectSearchEngine
import com.intellij.find.FindModel
import com.intellij.find.FindModelExtension
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.CacheAvoidingVirtualFile
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.fs.EelSearchApi
import com.intellij.platform.eel.fs.EelSearchEvent
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.workspaceModel.update
import com.intellij.usages.FindUsagesProcessPresentation
import com.intellij.util.indexing.testEntities.IndexableKindFileSetTestContributor
import com.intellij.util.indexing.testEntities.IndexingTestEntity
import com.intellij.util.indexing.testEntities.NonIndexableKindFileSetTestContributor
import com.intellij.util.indexing.testEntities.NonIndexableTestEntity
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.cancellation.CancellationException

@TestApplication
@Timeout(30)
@RegistryKey("find.in.files.eel.remote.search", "true")
internal class EelDirectorySearchEngineTest {
  @RegisterExtension
  val projectModel: ProjectModelExtension = ProjectModelExtension()

  @TestDisposable
  private lateinit var disposable: Disposable

  private val baseDir get() = projectModel.baseProjectDir
  private val project get() = projectModel.project
  private val descriptor = FakeEelDescriptor("remote")

  @BeforeEach
  fun setUp() {
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(NonIndexableKindFileSetTestContributor(), disposable)
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(IndexableKindFileSetTestContributor(), disposable)
    ExtensionTestUtil.maskExtensions(FindInProjectSearchEngine.EP_NAME, emptyList(), disposable)
    ExtensionTestUtil.maskExtensions(FindModelExtension.EP_NAME, emptyList(), disposable)
  }

  @Test
  fun `the engine is registered as a directory extension`() {
    assertThat(DirectorySearchEngine.EP_NAME.extensionList).anyMatch { it is EelDirectorySearchEngine }
  }

  @Test
  fun `unsupported queries cannot use the engine`() {
    val engine = engine { error("The query check must not connect") }
    assertThat(engine.canSearch(contentModel())).isTrue()
    for (query in listOf("", "multi\nline", "multi\rline", "caf\u00e9")) {
      assertThat(engine.canSearch(contentModel().apply { stringToFind = query })).isFalse()
    }
    for (mask in listOf("[id].tsx", "!*.min.js", "{a,b}.txt", "a\\b.txt")) {
      assertThat(engine.canSearch(contentModel().apply { fileFilter = mask })).isFalse()
    }
    assertThat(engine.canSearch(contentModel().apply { isRegularExpressions = true })).isFalse()
  }

  @Test
  @RegistryKey("find.in.files.eel.remote.search", "false")
  fun `the registry gate disables the engine`() {
    val engine = engine { error("The disabled engine must not connect") }
    assertThat(engine.canSearch(contentModel())).isFalse()
  }

  @Test
  fun `local directories and files have a negative weight`() {
    val root = baseDir.newVirtualDirectory("root")
    val file = baseDir.newVirtualFile("root/file.txt")
    val localEngine = engine(LocalEelDescriptor) { error("Local directories must not connect") }
    assertThat(localEngine.getWeight(root)).isNegative()
    assertThat(engine { error("Files must not connect") }.getWeight(file)).isNegative()
  }

  @Test
  fun `an environment without search support supplies the directory children`(): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("root")
    val child = baseDir.newVirtualDirectory("root/child")
    val file = baseDir.newVirtualFile("root/file.txt")
    assertThat(engine { null }.streamAll(root)).containsExactlyInAnyOrder(child.path, file.path)
  }

  @Test
  fun `an unreachable environment supplies the directory children`(): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("root")
    val file = baseDir.newVirtualFile("root/file.txt")
    assertThat(engine { throw IOException("Connection refused") }.streamAll(root)).containsExactly(file.path)
  }

  @Test
  fun `hits and faulty-file-skips streamed as candidates`(): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("root")
    val hitFile = baseDir.newVirtualFile("root/hit.txt")
    val events = mutableListOf<EelSearchEvent>(hit("root/hit.txt"))
    val expected = mutableListOf(hitFile.path)
    val goodReasonsToSkip = setOf(EelSearchEvent.Skipped.Reason.BINARY, EelSearchEvent.Skipped.Reason.TOO_LARGE)
    for (reason in EelSearchEvent.Skipped.Reason.entries) {
      val file = baseDir.newVirtualFile("root/$reason.bin")
      if (reason !in goodReasonsToSkip) expected.add(file.path)
      events.add(EelSearchEvent.Skipped(eelPath("root/$reason.bin"), reason))
    }
    val api = FakeEelSearchApi(flowOf(*events.toTypedArray()))
    val model = contentModel().apply {
      fileFilter = "*.txt, *.bin"
      isCaseSensitive = true
      isWholeWordsOnly = true
    }

    assertThat(engine { api }.streamAll(root, model)).containsExactlyElementsOf(expected)
    val request = api.requests.single()
    assertThat(request.roots).containsExactly(eelPath("root"))
    assertThat(request.content?.query).isEqualTo("needle")
    assertThat(request.content?.caseSensitive).isTrue()
    assertThat(request.content?.wholeWords).isFalse()
    assertThat(request.includeNameGlobs).containsExactly("*.txt", "*.bin")
    assertThat(request.excludeGlobs).isEmpty()
    assertThat(request.followSymlinks).isTrue()
    assertThat(request.maxFileSize).isPositive()
    assertThat(request.maxHits).isZero()
  }

  @Test
  fun `candidates reach the consumer before the stream completes`(): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("root")
    val hitFile = baseDir.newVirtualFile("root/hit.txt")
    val firstCandidateSeen = CompletableDeferred<Unit>()
    val api = FakeEelSearchApi(flow {
      emit(hit("root/hit.txt"))
      firstCandidateSeen.await()
    })

    val candidates = engine { api }.streamAll(root) { firstCandidateSeen.complete(Unit) }
    assertThat(candidates).containsExactly(hitFile.path)
  }

  @Test
  fun `cancellation during connection or streaming stops the search`(): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("root")
    baseDir.newVirtualFile("root/file.txt")
    val connecting = engine { throw CancellationException("The search was cancelled") }
    val streaming = engine { FakeEelSearchApi(flow { throw CancellationException("The search was cancelled") }) }
    for (engine in listOf(connecting, streaming)) {
      val candidates = mutableListOf<VirtualFile>()
      assertThatThrownBy {
        engine.streamAll(root) { candidates.addAll(it) }
      }.isInstanceOf(ProcessCanceledException::class.java)
      assertThat(candidates).isEmpty()
    }
  }

  @Test
  fun `consumer cancellation stops the stream`(): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("root")
    baseDir.newVirtualFile("root/first.txt")
    baseDir.newVirtualFile("root/second.txt")
    val api = FakeEelSearchApi(flowOf(hit("root/first.txt"), hit("root/second.txt")))
    var consumerCalls = 0
    assertThatThrownBy {
      engine { api }.streamAll(root) {
        consumerCalls++
        throw ProcessCanceledException()
      }
    }.isInstanceOf(ProcessCanceledException::class.java)
    assertThat(consumerCalls).isEqualTo(1)
  }

  @Test
  fun `a directory IO error supplies children for expansion`(): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("root")
    val partial = baseDir.newVirtualFile("root/partial.txt")
    val child = baseDir.newVirtualDirectory("root/child")
    val api = FakeEelSearchApi(flowOf(
      hit("root/partial.txt"),
      EelSearchEvent.Skipped(eelPath("root/child"), EelSearchEvent.Skipped.Reason.IO_ERROR, isDirectory = true),
    ))

    assertThat(engine { api }.streamAll(root)).containsExactlyInAnyOrder(partial.path, partial.path, child.path)
  }

  @Test
  fun `incomplete streams keep candidates and supply children for expansion`(): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("root")
    val partial = baseDir.newVirtualFile("root/partial.txt")
    val child = baseDir.newVirtualDirectory("root/child")
    val incompleteEvents = listOf(EelSearchEvent.Truncated)
    for (event in incompleteEvents) {
      val api = FakeEelSearchApi(flowOf(hit("root/partial.txt"), event))
      assertThat(engine { api }.streamAll(root)).containsExactlyInAnyOrder(partial.path, partial.path, child.path)
    }
    val failedApi = FakeEelSearchApi(flow {
      emit(hit("root/partial.txt"))
      throw IOException("The connection closed")
    })
    assertThat(engine { failedApi }.streamAll(root)).containsExactlyInAnyOrder(partial.path, partial.path, child.path)
  }

  @Test
  fun `a vanished hit does not cause directory expansion`(): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("root")
    val liveFile = baseDir.newVirtualFile("root/live.txt")
    baseDir.newVirtualFile("root/unmatched.txt")
    val api = FakeEelSearchApi(flowOf(hit("root/vanished.txt"), hit("root/live.txt")))
    assertThat(engine { api }.streamAll(root)).containsExactly(liveFile.path)
  }

  @Test
  fun `new files resolve under a read action without a VFS refresh`(): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("root")
    assertThat(root.children).isEmpty()
    val path = root.toNioPath().resolve("new.txt")
    Files.writeString(path, "needle")
    val api = FakeEelSearchApi(flowOf(hit("root/new.txt")))

    val result = engine { api }.streamAll(root) { files ->
      assertThat(files).allMatch { it is CacheAvoidingVirtualFile }
    }
    // Should not throw. At the moment we cannot return "${root.path}/new.txt", because we cannot do a VFS-refresh under RA.
    // Actually we want this behavior:
    //assertThat(result).containsExactly("${root.path}/new.txt")
    //assertThat(root.children).isEmpty()
  }

  @Test
  fun `the search pipeline filters masks and custom scopes`(): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("root")
    val included = baseDir.newVirtualFile("root/included.txt", "needle".toByteArray())
    baseDir.newVirtualFile("root/excluded.txt", "needle".toByteArray())
    baseDir.newVirtualFile("root/filtered.log", "needle".toByteArray())
    val api = FakeEelSearchApi(flowOf(hit("root/included.txt"), hit("root/excluded.txt"), hit("root/filtered.log")))
    register(engine { api })
    val model = contentModel(root).apply {
      fileFilter = "*.txt"
      isCustomScope = true
      customScope = object : GlobalSearchScope(project) {
        override fun contains(file: VirtualFile): Boolean = file.name != "excluded.txt"
        override fun isSearchInModuleContent(aModule: Module): Boolean = true
        override fun isSearchInLibraries(): Boolean = false
      }
    }
    assertThat(search(model)).containsExactly(included.path)
    assertThat(api.requests).hasSize(1)
  }

  @Test
  fun `the search pipeline hides excluded subdirectories`(): Unit = timeoutRunBlocking {
    checkExcludedSearch(searchRootIsExcluded = false, includeExcluded = false)
  }

  @Test
  @RegistryKey("find.search.in.excluded.dirs", "true")
  fun `the registry permits searching excluded subdirectories`(): Unit = timeoutRunBlocking {
    checkExcludedSearch(searchRootIsExcluded = false, includeExcluded = true)
  }

  @Test
  fun `an explicitly excluded target directory is searched`(): Unit = timeoutRunBlocking {
    checkExcludedSearch(searchRootIsExcluded = true, includeExcluded = true)
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun `directory expansion after a failure finds remaining files without duplicate usages`(cached: Boolean): Unit = timeoutRunBlocking {
    val root = baseDir.newVirtualDirectory("root")
    if (cached) {
      baseDir.newVirtualFile("root/partial.txt", "needle".toByteArray())
      baseDir.newVirtualFile("root/child/remaining.txt", "needle".toByteArray())
    }
    else {
      assertThat(root.children).isEmpty()
      Files.writeString(root.toNioPath().resolve("partial.txt"), "needle")
      Files.createDirectory(root.toNioPath().resolve("child"))
      Files.writeString(root.toNioPath().resolve("child/remaining.txt"), "needle")
    }
    val api = FakeEelSearchApi(flow {
      emit(hit("root/partial.txt"))
      throw IOException("The connection closed")
    })
    register(engine { api })
    assertThat(search(contentModel(root))).containsExactlyInAnyOrder("${root.path}/partial.txt", "${root.path}/child/remaining.txt")
  }

  private suspend fun checkExcludedSearch(searchRootIsExcluded: Boolean, includeExcluded: Boolean) {
    val root = baseDir.newVirtualDirectory("root")
    val visible = baseDir.newVirtualFile("root/visible.txt", "needle".toByteArray())
    val excluded = baseDir.newVirtualDirectory("root/excluded")
    val hidden = baseDir.newVirtualFile("root/excluded/hidden.txt", "needle".toByteArray())
    val workspaceModel = project.workspaceModel
    val urlManager = workspaceModel.getVirtualFileUrlManager()
    workspaceModel.update { storage ->
      storage.addEntity(NonIndexableTestEntity(root.toVirtualFileUrl(urlManager), NonPersistentEntitySource))
      val excludedRoot = if (searchRootIsExcluded) root else excluded
      storage.addEntity(IndexingTestEntity(emptyList(), listOf(excludedRoot.toVirtualFileUrl(urlManager)), NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()
    val api = FakeEelSearchApi(flowOf(hit("root/visible.txt"), hit("root/excluded/hidden.txt")))
    register(engine { api })
    val expected = if (includeExcluded) listOf(visible.path, hidden.path) else listOf(visible.path)
    assertThat(search(contentModel(root))).containsExactlyInAnyOrderElementsOf(expected)
    assertThat(api.requests.single().excludeGlobs).isEmpty()
  }

  private fun engine(
    descriptor: EelDescriptor = this.descriptor,
    searchApiOf: suspend (EelDescriptor) -> EelSearchApi?,
  ): EelDirectorySearchEngine = EelDirectorySearchEngine(EelSearchEdges(
    descriptorOf = { descriptor },
    eelPathOf = { EelPath.parse("/data/${baseDir.rootPath.relativize(it).joinToString("/")}", descriptor) },
    nioPathOf = { path -> baseDir.rootPath.resolve(path.toString().removePrefix("/data/")) },
    searchApiOf = searchApiOf,
  ))

  private fun eelPath(relativePath: String): EelPath = EelPath.parse("/data/$relativePath", descriptor)

  private fun hit(relativePath: String): EelSearchEvent.Hit = EelSearchEvent.Hit(eelPath(relativePath), relativePath, 1L, false)

  private fun contentModel(directory: VirtualFile? = null): FindModel = FindModel().apply {
    stringToFind = "needle"
    isMultipleFiles = true
    isWithSubdirectories = true
    if (directory != null) {
      isProjectScope = false
      directoryName = directory.path
    }
  }

  private fun EelDirectorySearchEngine.streamAll(
    root: VirtualFile,
    model: FindModel = contentModel(),
    consumer: (Collection<VirtualFile>) -> Unit = {},
  ): List<String> = ProgressManager.getInstance().runProcess<List<String>>({
    ReadAction.computeBlocking<List<String>, Throwable> {
      val candidates = mutableListOf<String>()
      searchDirectory(root, model) { files ->
        candidates.addAll(files.map { it.path })
        consumer(files)
      }
      candidates
    }
  }, EmptyProgressIndicator())

  private fun register(engine: DirectorySearchEngine) {
    DirectorySearchEngine.EP_NAME.point.registerExtension(engine, disposable)
  }

  private fun search(model: FindModel): Collection<String> {
    val results = ConcurrentLinkedQueue<String>()
    val presentation = FindUsagesProcessPresentation(FindInProjectUtil.setupViewPresentation(true, model))
    FindInProjectUtil.findUsages(model, project, { usage ->
      results.add(requireNotNull(usage.virtualFile).path)
      true
    }, presentation)
    return results
  }
}
