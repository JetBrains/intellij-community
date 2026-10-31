// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find

import com.intellij.codeWithMe.ClientId
import com.intellij.find.DirectorySearchEngine.FileSearchCandidate
import com.intellij.find.FindInProjectSearchEngine.Coverage
import com.intellij.find.FindInProjectSearchEngine.FindInProjectSearcher
import com.intellij.find.FindInProjectSearchEngine.ScanObserver
import com.intellij.find.impl.FindInProjectUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.progress.util.ProgressWrapper
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.CacheAvoidingVirtualFile
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.search.LocalSearchScope
import com.intellij.testFramework.IndexingTestUtil.Companion.waitUntilIndexesAreReady
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.usageView.UsageInfo
import com.intellij.util.LocalTimeCounter
import com.intellij.util.Processor
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.indexing.UnindexedFilesUpdater
import com.intellij.util.indexing.testEntities.NonIndexableKindFileSetTestContributor
import com.intellij.util.indexing.testEntities.NonIndexableTestEntity
import com.intellij.util.ui.EDT
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.extension.RegisterExtension
import java.lang.management.ManagementFactory
import java.util.Collections.synchronizedList
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport
import java.util.function.Consumer
import kotlin.time.Duration.Companion.seconds

/**
 * Pins the threading and cancellation contract of a "Find in Path" search through [FindInProjectUtil.findUsages]:
 * which thread and lock state each callback sees, how often it is called, that a streaming searcher may feed from several threads,
 * what an outside cancel (of the indicator or of the parent coroutine) leaves behind, that the workers yield to a write action,
 * and that they scan in parallel.
 * Results and phase order are pinned by [FindInProjectPipelineTest]; this suite pins what the work queue and its worker
 * coroutines must keep, whatever their implementation.
 * The files `s1..s3` and `w1..w3` live in a non-indexable root, so the walk finds all of them; each holds the pattern `data` once.
 */
@TestApplication
@RegistryKey("find.in.files.in.non.indexable.enable", "true")
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class FindInProjectContractTest {
  private companion object {
    /** The work queue and its worker coroutines: `FindWorkQueue`, its nested classes and lambdas. */
    const val WORK_QUEUE_CLASS_PREFIX = "com.intellij.find.impl.FindWorkQueue"

    /** The threads of one streaming searcher that feed its processor at once. */
    const val FEEDER_COUNT = 4
  }

  @RegisterExtension
  private val projectModel: ProjectModelExtension = ProjectModelExtension()
  private val baseDir get() = projectModel.baseProjectDir
  private val project get() = projectModel.project

  @TestDisposable
  private lateinit var disposable: Disposable

  private lateinit var searcherFiles: List<VirtualFile>
  private lateinit var walkFiles: List<VirtualFile>

  @BeforeEach
  fun setup(): Unit = runBlocking {
    WorkspaceFileIndexImpl.EP_NAME.point.registerExtension(NonIndexableKindFileSetTestContributor(), disposable)

    val root = baseDir.newVirtualDirectory("non-indexable")
    searcherFiles = (1..3).map { baseDir.newVirtualFile("non-indexable/s$it.txt", "searcher file $it with data".toByteArray()) }
    walkFiles = (1..3).map { baseDir.newVirtualFile("non-indexable/w$it.txt", "walk file $it with data".toByteArray()) }

    val urlManager = project.workspaceModel.getVirtualFileUrlManager()
    project.workspaceModel.update("add non-indexable root") { storage ->
      storage.addEntity(NonIndexableTestEntity(root.toVirtualFileUrl(urlManager), NonPersistentEntitySource))
    }
    VfsTestUtil.syncRefresh()
    waitUntilIndexesAreReady(project)
  }

  // 1. searcher calls

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  fun `searchers run without read access off the EDT, under the search indicator, and each is asked once`() {
    val collecting = registerSearcher(streaming = false, search = { processor -> searcherFiles.take(2).all { processor.process(it) } })
    val streaming = registerSearcher(streaming = true, search = { processor -> processor.process(searcherFiles[2]) })

    val indicator = ProgressIndicatorBase()
    val usages = synchronizedList(ArrayList<String>())
    findUsages(projectModel("data"), indicator = indicator) { usages.add(it.virtualFile!!.name) }

    assertThat(usages).containsExactlyInAnyOrder("s1.txt", "s2.txt", "s3.txt", "w1.txt", "w2.txt", "w3.txt")
    assertThat(collecting.calls("processOccurrences")).hasSize(1)
    assertThat(collecting.calls("searchForOccurrences")).hasSize(1)
    assertThat(streaming.calls("processOccurrences")).hasSize(1)
    assertThat(streaming.calls("searchForOccurrences")).describedAs("a streaming searcher is never asked to collect").isEmpty()
    for (call in collecting.calls("processOccurrences") + collecting.calls("searchForOccurrences") + streaming.calls("processOccurrences")) {
      assertThat(call.readAccess).describedAs("read access in ${call.name}").isFalse()
      assertThat(call.edt).describedAs("EDT in ${call.name}").isFalse()
      // a searcher may read the indicator of its thread, e.g. for TooManyUsagesStatus
      assertThat(call.indicator).describedAs("unwrapped indicator in ${call.name}").isSameAs(indicator)
    }
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  fun `the coverage arrives with the return of processOccurrences and ALL_CANDIDATES of a reliable searcher skips the walk`() {
    // nothing else is asked about the coverage: the searcher decides it when its enumeration ends, and returns it
    val searcher = registerSearcher(streaming = true, coverage = Coverage.ALL_CANDIDATES,
                                    search = { processor -> searcherFiles.all { processor.process(it) } })

    val usages = synchronizedList(ArrayList<String>())
    findUsages(projectModel("data")) { usages.add(it.virtualFile!!.name) }

    assertThat(usages).containsExactlyInAnyOrder("s1.txt", "s2.txt", "s3.txt")
    assertThat(searcher.calls("processOccurrences")).hasSize(1)
    assertThat(searcher.calls("isCovered")).describedAs("the non-indexable walk is skipped").isEmpty()
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  fun `a streaming searcher may feed files from several threads at once, each file is scanned once`() {
    val fed = searcherFiles + createConcurrentFiles()
    val searcher = registerSearcher(streaming = true, search = { processor ->
      feedFromThreads(fed) { files -> files.all { processor.process(it) } }.all { it }
    })

    val usages = synchronizedList(ArrayList<String>())
    findUsages(projectModel("data")) { usages.add(it.virtualFile!!.name) }

    // each file holds the pattern once, so one usage per file means one scan per file
    val expected = (fed + walkFiles).map { it.name }
    assertThat(usages).containsExactlyInAnyOrderElementsOf(expected)
    assertThat(searcher.calls("fileScanned").map { it.file }).containsExactlyInAnyOrderElementsOf(expected)
    assertThat(searcher.coverages()).containsExactly(Coverage.INDEXED_ONLY)
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  fun `a stop makes every concurrent feeder's processor return false`() {
    val events = Events()
    val fed = searcherFiles + createConcurrentFiles()
    val feederSawFalse = synchronizedList(ArrayList<Boolean>())
    val searcher = registerSearcher(streaming = true, events = events, search = { processor ->
      // each feeder offers its files again and again until its processor returns false
      val sawFalse = feedFromThreads(fed) { files ->
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        var stopped = false
        while (!stopped && System.nanoTime() < deadline) {
          stopped = !files.all { processor.process(it) }
          if (!stopped) LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1))
        }
        stopped
      }
      feederSawFalse.addAll(sawFalse)
      sawFalse.none { it }
    })

    val usages = synchronizedList(ArrayList<String>())
    findUsages(projectModel("data")) { usages.add(it.virtualFile!!.name); false }

    assertThat(usages).isNotEmpty()
    assertThat(feederSawFalse).describedAs("the feeders whose processor returned false").hasSize(FEEDER_COUNT).containsOnly(true)
    assertThat(searcher.coverages()).containsExactly(Coverage.STOPPED)
    assertThat(events.snapshot()).doesNotContain("completed")
  }

  // 2. DirectorySearchEngine.canSearch

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  fun `canSearch runs once per directory search without read access and gets the model of the search`() {
    val root = baseDir.newVirtualDirectory("directory")
    baseDir.newVirtualFile("directory/a.txt", "a with data".toByteArray())
    baseDir.newVirtualFile("directory/sub/b.txt", "b with data".toByteArray())
    VfsTestUtil.syncRefresh()

    val canSearchCalls = synchronizedList(ArrayList<Call>())
    val canSearchModels = synchronizedList(ArrayList<FindModel>())
    val searchedModels = synchronizedList(ArrayList<FindModel>())
    val engine = object : DirectorySearchEngine {
      override fun canSearch(findModel: FindModel): Boolean {
        canSearchCalls.add(Call.here("canSearch"))
        canSearchModels.add(findModel)
        return true
      }

      override fun canSearchNames(): Boolean = false

      override fun getWeight(directory: VirtualFile): Int = 100

      override fun searchDirectory(directory: VirtualFile, findModel: FindModel, consumer: Consumer<in Collection<VirtualFile>>) {
        searchedModels.add(findModel)
        consumer.accept(directory.children.toList())
      }

      override fun searchNames(directory: VirtualFile, pathPattern: String, consumer: Consumer<FileSearchCandidate>) {
        error("Name search is not supported.")
      }
    }
    DirectorySearchEngine.EP_NAME.point.registerExtension(engine, disposable)

    val model = directoryModel(root, "data")
    val usages = synchronizedList(ArrayList<String>())
    findUsages(model) { usages.add(it.virtualFile!!.name) }

    assertThat(usages).containsExactlyInAnyOrder("a.txt", "b.txt")
    assertThat(canSearchCalls).hasSize(1)
    assertThat(canSearchCalls[0].readAccess).describedAs("read access in canSearch").isFalse()
    assertThat(canSearchCalls[0].edt).describedAs("EDT in canSearch").isFalse()
    assertThat(canSearchModels[0]).isSameAs(model)
    assertThat(searchedModels).isNotEmpty()
    assertThat(searchedModels.all { it === model }).describedAs("searchDirectory gets the model of the search").isTrue()
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  fun `the files of an excluded searched directory are kept when the directory is transient and the engine gives cached files`() {
    val module = projectModel.createModule()
    val content = baseDir.newVirtualDirectory("content")
    baseDir.newVirtualFile("content/kept.txt", "kept file with data".toByteArray())
    // the excluded directory enters the VFS with its children unlisted, so `gen` stays out of the VFS until a lookup caches it
    baseDir.newFileNio("content/excluded/gen/hit.txt", "hit file with data".toByteArray())
    val excluded = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(baseDir.rootPath.resolve("content/excluded")))
    // the root and its exclusion arrive in one change: the scan of a content root lists every directory that is not excluded yet
    ModuleRootModificationUtil.updateModel(module) { model -> model.addContentEntry(content).addExcludeFolder(excluded) }
    VfsTestUtil.syncRefresh()
    waitUntilIndexesAreReady(project)
    // the engine resolves the directory by path, as the index searcher and the Eel engine do, so its files are cached VFS files
    registerDirectoryEngine { directory, consumer ->
      val cached = checkNotNull(LocalFileSystem.getInstance().findFileByPath(directory.path))
      consumer.accept(cached.children.toList())
    }

    val model = directoryModel("${excluded.path}/gen", "data")
    val directory = checkNotNull(FindInProjectUtil.getDirectory(model))
    assertThat(directory).isInstanceOf(CacheAvoidingVirtualFile::class.java)
    assertThat((directory as CacheAvoidingVirtualFile).isCached).describedAs("the searched directory is cached already").isFalse()
    val usages = synchronizedList(ArrayList<String>())
    findUsages(model) { usages.add(it.virtualFile!!.name) }

    assertThat(usages).containsExactly("hit.txt")
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  fun `a stop while a directory is listed makes the walk take no more children from the engine`() {
    assumeTrue(UnindexedFilesUpdater.getNumberOfScanningThreads() >= 2, "a single scanning thread")
    val root = baseDir.newVirtualDirectory("listed")
    val files = (1..20).map { baseDir.newVirtualFile("listed/f$it.txt", "listed file $it with data".toByteArray()) }
    VfsTestUtil.syncRefresh()

    val pulled = synchronizedList(ArrayList<Int>())
    registerDirectoryEngine { directory, consumer ->
      if (directory.path != root.path) {
        consumer.accept(directory.children.toList())
        return@registerDirectoryEngine
      }
      // the usage of the first file stops the search on another worker, while this worker still lists the directory
      consumer.accept(files.take(1))
      // the same batch again and again: a live search takes all of it (the files offered before are skipped), a stopped one
      // takes less, and nothing of the batch after
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
      while (System.nanoTime() < deadline) {
        ProgressManager.checkCanceled()
        val count = AtomicInteger()
        consumer.accept(CountingList(files, count))
        pulled.add(count.get())
        if (count.get() < files.size) {
          val after = AtomicInteger()
          consumer.accept(CountingList(files, after))
          pulled.add(after.get())
          return@registerDirectoryEngine
        }
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1))
      }
    }

    val usages = synchronizedList(ArrayList<String>())
    findUsages(directoryModel(root, "data")) { usages.add(it.virtualFile!!.name); false }

    assertThat(usages).isNotEmpty()
    assertThat(pulled).describedAs("the children taken of each batch").hasSizeGreaterThanOrEqualTo(2)
    assertThat(pulled.dropLast(2)).describedAs("the batches taken whole before the stop").allMatch { it == files.size }
    assertThat(pulled[pulled.size - 2]).describedAs("the children taken of the batch of the stop, of $pulled").isLessThan(files.size)
    assertThat(pulled.last()).describedAs("the children taken of the batch after the stop, of $pulled").isZero()
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  fun `a directory whose listing is restarted by a write action delivers each file once`() {
    val root = baseDir.newVirtualDirectory("relisted")
    val top = (1..6).map { baseDir.newVirtualFile("relisted/f$it.txt", "relisted file $it with data".toByteArray()) }
    val nested = baseDir.newVirtualFile("relisted/sub/n.txt", "nested file with data".toByteArray())
    VfsTestUtil.syncRefresh()
    val sub = nested.parent

    val rootListings = AtomicInteger()
    val write = CompletableFuture<Void>()
    registerDirectoryEngine { directory, consumer ->
      if (directory.path != root.path) {
        consumer.accept(directory.children.toList())
        return@registerDirectoryEngine
      }
      if (rootListings.incrementAndGet() == 1) {
        // a part of the children, a directory among them, then a pending write action cancels this read action
        consumer.accept(listOf(sub) + top.take(3))
        CompletableFuture.runAsync({ WriteAction.runAndWait<RuntimeException> {} }, AppExecutorUtil.getAppExecutorService())
          .whenComplete { _, e -> if (e == null) write.complete(null) else write.completeExceptionally(e) }
        waitForCancellation()
      }
      // the restarted item lists the directory again and offers every child, those offered before too
      consumer.accept(listOf(sub) + top)
    }

    val usages = synchronizedList(ArrayList<String>())
    findUsages(directoryModel(root, "data")) { usages.add(it.virtualFile!!.name) }

    write.get(30, TimeUnit.SECONDS)
    assertThat(rootListings.get()).describedAs("listings of the restarted directory").isEqualTo(2)
    // each file holds one usage, so the exact list also fails on a file scanned twice
    assertThat(usages).containsExactlyInAnyOrderElementsOf(top.map { it.name } + nested.name)
  }

  // 3. worker callbacks

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  fun `isCovered, fileScanned and the usage processor run under read access off the EDT, under the search indicator`() {
    val searcher = registerSearcher(streaming = false, search = { processor -> processor.process(searcherFiles[0]) })
    val indicator = ProgressIndicatorBase()
    val usageCalls = synchronizedList(ArrayList<Call>())
    val usageIndicators = synchronizedList(ArrayList<ProgressIndicator?>())

    findUsages(projectModel("data"), indicator = indicator) { usage ->
      usageCalls.add(Call.here("usage:${usage.virtualFile!!.name}"))
      usageIndicators.add(ProgressManager.getInstance().progressIndicator?.let { ProgressWrapper.unwrapAll(it) })
      true
    }

    // isCovered: the WALK candidates only; fileScanned: the disk-loaded files
    assertThat(searcher.calls("isCovered").map { it.file }).containsExactlyInAnyOrder("s2.txt", "s3.txt", "w1.txt", "w2.txt", "w3.txt")
    assertThat(searcher.calls("fileScanned").map { it.file })
      .containsExactlyInAnyOrder("s1.txt", "s2.txt", "s3.txt", "w1.txt", "w2.txt", "w3.txt")
    assertThat(usageCalls).hasSize(6)
    for (call in searcher.calls("isCovered") + searcher.calls("fileScanned") + usageCalls) {
      assertThat(call.readAccess).describedAs("read access in ${call.name}").isTrue()
      assertThat(call.edt).describedAs("EDT in ${call.name}").isFalse()
    }
    // the usage view and the popup find TooManyUsagesStatus through the unwrapped indicator of the thread
    assertThat(usageIndicators).hasSize(6)
    assertThat(usageIndicators.all { it === indicator }).describedAs("the unwrapped indicators $usageIndicators").isTrue()
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  fun `the usage processor runs under the ClientId the search started under`() {
    val searchClientId = ClientId("find-contract-client")
    val usageClientIds = synchronizedList(ArrayList<ClientId>())

    ClientId.withExplicitClientId(searchClientId) {
      findUsages(projectModel("data")) { usage ->
        usageClientIds.add(ClientId.current)
        true
      }
    }

    assertThat(usageClientIds).isNotEmpty()
    assertThat(usageClientIds).describedAs("the ClientIds of the usages").containsOnly(searchClientId)
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  fun `a local scope of two light files with one name and URL finds the usages of both`() {
    val files = runReadActionBlocking {
      (1..2).map {
        //event system enabled, so the PSI file has its virtual file:
        PsiFileFactory.getInstance(project).createFileFromText("light.txt", PlainTextFileType.INSTANCE, "light $it with data", LocalTimeCounter.currentTime(), true)
      }
    }
    assertThat(files[0].virtualFile.url).isEqualTo(files[1].virtualFile.url)
    val model = FindModel().apply {
      stringToFind = "data"
      isMultipleFiles = true
      isProjectScope = false
      isCustomScope = true
      customScope = LocalSearchScope(files.toTypedArray())
    }
    val usageFiles = synchronizedList(ArrayList<VirtualFile>())

    findUsages(model) { usage ->
      usageFiles.add(usage.virtualFile!!)
      true
    }

    assertThat(usageFiles).hasSize(2)
    assertThat(usageFiles.toSet()).containsExactlyInAnyOrder(files[0].virtualFile, files[1].virtualFile)
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  fun `the usages of one file arrive on one thread, in order and without usages of other files in between`() {
    val multi = (1..8).map { i ->
      baseDir.newVirtualFile("non-indexable/multi$i.txt", (1..5).joinToString("\n") { "line $it of $i with data" }.toByteArray())
    }
    VfsTestUtil.syncRefresh()

    val perThread = ConcurrentHashMap<Thread, MutableList<Pair<String, Int>>>()
    findUsages(projectModel("data")) { usage ->
      perThread.computeIfAbsent(Thread.currentThread()) { ArrayList() }.add(usage.virtualFile!!.name to usage.navigationOffset)
      true
    }

    val threadsOfFile = HashMap<String, MutableSet<Thread>>()
    for ((thread, usages) in perThread) {
      for ((file, _) in usages) threadsOfFile.getOrPut(file) { HashSet() }.add(thread)
      // the usages of one file are one contiguous run on its thread, with growing offsets (FindInFilesApiImpl merges adjacent ones)
      val runs = usages.fold(ArrayList<MutableList<Pair<String, Int>>>()) { acc, usage ->
        if (acc.lastOrNull()?.last()?.first == usage.first) acc.last().add(usage) else acc.add(arrayListOf(usage))
        acc
      }
      assertThat(runs.map { it.first().first }).describedAs("files in the order of thread ${thread.name}").doesNotHaveDuplicates()
      for (run in runs) {
        assertThat(run.map { it.second }).describedAs("offsets of ${run.first().first}").isSorted()
      }
    }
    assertThat(threadsOfFile.keys).containsAll(multi.map { it.name })
    assertThat(threadsOfFile.filterValues { it.size != 1 }.keys).describedAs("files whose usages came on several threads").isEmpty()
    assertThat(perThread.values.sumOf { list -> list.count { it.first.startsWith("multi") } }).isEqualTo(8 * 5)
  }

  // 4. outside cancel

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  fun `an outside cancel ends the search with ProcessCanceledException and leaves no scan and no worker behind`() {
    val events = Events()
    val firstUsage = CountDownLatch(1)
    val streaming = CountDownLatch(1)
    val released = CountDownLatch(1)
    registerSearcher(streaming = true, events = events, search = { processor ->
      processor.process(searcherFiles[0])
      check(firstUsage.await(30, TimeUnit.SECONDS)) { "the first file was not scanned" }
      streaming.countDown()
      check(released.await(30, TimeUnit.SECONDS)) { "the test did not release the searcher" }
      processor.process(searcherFiles[1]) // offered after the cancel; it must not be scanned
      true
    })

    val indicator = ProgressIndicatorBase()
    val search = AppExecutorUtil.getAppExecutorService().submit {
      findUsages(projectModel("data"), indicator = indicator) { usage ->
        events.add("usage:${usage.virtualFile!!.name}")
        if (usage.virtualFile == searcherFiles[0]) firstUsage.countDown()
        true
      }
    }

    assertThat(streaming.await(30, TimeUnit.SECONDS)).describedAs("the streaming searcher started").isTrue()
    indicator.cancel()
    events.add("cancel returned")
    released.countDown()

    val error = try {
      search.get(30, TimeUnit.SECONDS)
      null
    }
    catch (e: ExecutionException) {
      e.cause
    }
    assertThat(error).isInstanceOf(ProcessCanceledException::class.java)

    // findUsages joins every worker before it throws, so no wait is needed
    assertThat(workQueueThreads()).describedAs("a worker still runs").isEmpty()
    val log = events.snapshot()
    assertThat(log).contains("usage:s1.txt", "scanned:s1.txt").doesNotContain("completed")
    assertThat(log.drop(log.indexOf("cancel returned") + 1)).describedAs("events after the cancel returned").isEmpty()
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  fun `a cancel of the parent coroutine ends the search and leaves no worker behind`(): Unit = runBlocking {
    val events = Events()
    val firstUsage = CountDownLatch(1)
    val streaming = CountDownLatch(1)
    val released = CountDownLatch(1)
    registerSearcher(streaming = true, events = events, search = { processor ->
      processor.process(searcherFiles[0])
      check(firstUsage.await(30, TimeUnit.SECONDS)) { "the first file was not scanned" }
      streaming.countDown()
      check(released.await(30, TimeUnit.SECONDS)) { "the test did not release the searcher" }
      processor.process(searcherFiles[1]) // offered after the cancel; it must not be scanned
      true
    })

    // the popup path with the new find key: FindInFilesApiImpl runs the search in coroutineToIndicator of its flow coroutine
    val thrown = CompletableFuture<Throwable?>()
    val job = launch(Dispatchers.IO) {
      coroutineToIndicator {
        try {
          findUsages(projectModel("data")) { usage ->
            events.add("usage:${usage.virtualFile!!.name}")
            if (usage.virtualFile == searcherFiles[0]) firstUsage.countDown()
            true
          }
          thrown.complete(null)
        }
        catch (e: Throwable) {
          thrown.complete(e)
          throw e
        }
      }
    }

    assertThat(streaming.await(30, TimeUnit.SECONDS)).describedAs("the streaming searcher started").isTrue()
    job.cancel()
    events.add("cancel returned")
    released.countDown()
    withTimeout(30.seconds) { job.join() }

    assertThat(job.isCancelled).describedAs("the search coroutine is cancelled").isTrue()
    assertThat(thrown.get(1, TimeUnit.SECONDS)).describedAs("what findUsages threw").isInstanceOf(CancellationException::class.java)
    // findUsages joins every worker before it throws, and the job completed after it
    assertThat(workQueueThreads()).describedAs("a worker still runs").isEmpty()
    val log = events.snapshot()
    assertThat(log).contains("usage:s1.txt", "scanned:s1.txt").doesNotContain("completed")
    assertThat(log.drop(log.indexOf("cancel returned") + 1)).describedAs("events after the cancel returned").isEmpty()
  }

  // 5. write-action priority

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  fun `a write action completes while the workers scan, and no file is lost`() {
    val many = (1..40).map { baseDir.newVirtualFile("non-indexable/many/f$it.txt", "many file $it with data".toByteArray()) }
    VfsTestUtil.syncRefresh()

    val writeRequested = AtomicBoolean()
    val write = CompletableFuture<Long>()
    val usages = synchronizedList(ArrayList<String>())
    findUsages(projectModel("data")) { usage ->
      if (writeRequested.compareAndSet(false, true)) {
        val startNs = System.nanoTime()
        AppExecutorUtil.getAppExecutorService().execute {
          try {
            WriteAction.runAndWait<RuntimeException> {}
            write.complete(System.nanoTime() - startNs)
          }
          catch (e: Throwable) {
            write.completeExceptionally(e)
          }
        }
      }
      // each worker stays busy in its read action until the write action completed; only a yield lets it complete
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
      while (!write.isDone && System.nanoTime() < deadline) {
        ProgressManager.checkCanceled()
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1))
      }
      usages.add(usage.virtualFile!!.name)
      true
    }

    val writeNs = write.get(30, TimeUnit.SECONDS)
    assertThat(TimeUnit.NANOSECONDS.toMillis(writeNs)).describedAs("write action wait, ms").isLessThan(1000)
    // each file holds one usage, so the exact list also fails on a usage delivered twice after a restart
    assertThat(usages).containsExactlyInAnyOrderElementsOf(
      many.map { it.name } + listOf("s1.txt", "s2.txt", "s3.txt", "w1.txt", "w2.txt", "w3.txt"))
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  fun `a file whose scan is restarted by a write action delivers each usage once`() {
    val multi = baseDir.newVirtualFile("non-indexable/restarted.txt", (1..3).joinToString("\n") { "line $it with data" }.toByteArray())
    VfsTestUtil.syncRefresh()

    val write = CompletableFuture<Unit>()
    val attempts = synchronizedList(ArrayList<Int>())
    val delivered = synchronizedList(ArrayList<Int>())
    findUsages(projectModel("data")) { usage ->
      if (usage.virtualFile != multi) return@findUsages true
      attempts.add(usage.navigationOffset)
      if (attempts.size == 2) {
        // the second usage waits for a write action, which restarts the scan of the file after the first usage was delivered
        AppExecutorUtil.getAppExecutorService().execute {
          try {
            WriteAction.runAndWait<RuntimeException> {}
            write.complete(Unit)
          }
          catch (e: Throwable) {
            write.completeExceptionally(e)
          }
        }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (!write.isDone && System.nanoTime() < deadline) {
          ProgressManager.checkCanceled()
          LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1))
        }
      }
      delivered.add(usage.navigationOffset)
      true
    }

    write.get(30, TimeUnit.SECONDS)
    assertThat(delivered).describedAs("the delivered usages of the restarted file").hasSize(3).doesNotHaveDuplicates().isSorted()
    // the second usage was offered again after the restart, the first one was not:
    assertThat(attempts).describedAs("the usages offered to the processor").hasSize(4)
    assertThat(attempts[2]).isEqualTo(attempts[1])
  }

  // 6. parallelism

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  fun `two workers scan at the same time`() {
    assumeTrue(Runtime.getRuntime().availableProcessors() >= 2, "a single core")
    assumeTrue(UnindexedFilesUpdater.getNumberOfScanningThreads() >= 2, "a single scanning thread")
    (1..20).forEach { baseDir.newVirtualFile("non-indexable/par/f$it.txt", "parallel file $it with data".toByteArray()) }
    VfsTestUtil.syncRefresh()

    val inside: MutableSet<Thread> = ConcurrentHashMap.newKeySet()
    val overlapped = AtomicBoolean()
    findUsages(projectModel("data")) {
      val thread = Thread.currentThread()
      inside.add(thread)
      try {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (!overlapped.get() && System.nanoTime() < deadline) {
          if (inside.size >= 2) {
            overlapped.set(true)
          }
          else {
            ProgressManager.checkCanceled()
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1))
          }
        }
      }
      finally {
        inside.remove(thread)
      }
      true
    }

    assertThat(overlapped.get()).describedAs("two usage processors ran at once").isTrue()
  }

  // 7. speed baseline, by hand: FIND_CONTRACT_TIMING_DIR=<large directory>

  @Test
  @Timeout(value = 30, unit = TimeUnit.MINUTES)
  @EnabledIfEnvironmentVariable(named = "FIND_CONTRACT_TIMING_DIR", matches = ".+")
  fun `timing of a directory search over a large directory`() {
    val path = System.getenv("FIND_CONTRACT_TIMING_DIR")
    val pattern = System.getenv("FIND_CONTRACT_TIMING_PATTERN") ?: "ProgressManager"
    val directory = LocalFileSystem.getInstance().refreshAndFindFileByPath(path)
    checkNotNull(directory) { "no directory $path" }
    val threads = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
    val runs = (1..5).map {
      val usages = AtomicInteger()
      val startBytes = threads.totalThreadAllocatedBytes
      val startNs = System.nanoTime()
      findUsages(directoryModel(directory, pattern)) { usages.incrementAndGet(); true }
      val ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
      val mb = (threads.totalThreadAllocatedBytes - startBytes) / (1024 * 1024)
      println("FIND-CONTRACT-TIMING run $it: $ms ms, ${usages.get()} usages, allocated=$mb MB")
      ms to mb
    }
    val timesMs = runs.map { it.first }
    val allocatedMb = runs.map { it.second }
    println("FIND-CONTRACT-TIMING median ${timesMs.sorted()[timesMs.size / 2]} ms over $timesMs, " +
            "allocated=${allocatedMb.sorted()[allocatedMb.size / 2]} MB over $allocatedMb in $path for '$pattern'")
  }

  /** @return the threads that run code of the work queue now */
  private fun workQueueThreads(): List<String> {
    return Thread.getAllStackTraces().filter { (_, stack) ->
      stack.any { it.className.startsWith(WORK_QUEUE_CLASS_PREFIX) }
    }.keys.map { it.name }
  }

  /** One callback call: its name, its file (if any), and the thread state it saw: read access, EDT, the unwrapped indicator. */
  private data class Call(val name: String, val file: String?, val readAccess: Boolean, val edt: Boolean, val indicator: ProgressIndicator?) {
    companion object {
      fun here(name: String, file: VirtualFile? = null): Call =
        Call(name, file?.name, ApplicationManager.getApplication().isReadAccessAllowed, EDT.isCurrentThreadEdt(),
             ProgressManager.getInstance().progressIndicator?.let { ProgressWrapper.unwrapAll(it) })
    }
  }

  /** The calls one fake searcher received, and what its [FindInProjectSearcher.processOccurrences] returned. */
  private class SearcherCalls {
    private val list = synchronizedList(ArrayList<Call>())
    private val returned = synchronizedList(ArrayList<Coverage>())

    fun add(call: Call) {
      list.add(call)
    }

    fun returned(coverage: Coverage) {
      returned.add(coverage)
    }

    fun calls(name: String): List<Call> = synchronized(list) { list.filter { it.name == name } }

    fun coverages(): List<Coverage> = synchronized(returned) { ArrayList(returned) }
  }

  /** The events of one test in arrival order, from any thread. */
  private class Events {
    private val list = synchronizedList(ArrayList<String>())

    fun add(event: String) {
      list.add(event)
    }

    fun snapshot(): List<String> = synchronized(list) { ArrayList(list) }
  }

  /**
   * Registers a reliable searcher that covers no file and observes the scan; [events] records `scanned:<name>` and `completed`.
   * Its [FindInProjectSearcher.processOccurrences] returns [coverage] unless [search] returned false.
   */
  private fun registerSearcher(streaming: Boolean,
                               events: Events? = null,
                               coverage: Coverage = Coverage.INDEXED_ONLY,
                               search: (Processor<in VirtualFile>) -> Boolean): SearcherCalls {
    val calls = SearcherCalls()
    val engine = object : FindInProjectSearchEngine {
      override fun createSearcher(findModel: FindModel, project: Project): FindInProjectSearcher {
        return object : ScanObserver {
          override fun searchForOccurrences(): Collection<VirtualFile> {
            calls.add(Call.here("searchForOccurrences"))
            val files = ArrayList<VirtualFile>()
            search(Processor { files.add(it) })
            return files
          }

          override fun processOccurrences(processor: Processor<in VirtualFile>): Coverage {
            calls.add(Call.here("processOccurrences"))
            val completed = if (streaming) search(processor) else super.processOccurrences(processor) != Coverage.STOPPED
            val result = if (completed) coverage else Coverage.STOPPED
            calls.returned(result)
            return result
          }

          override fun isStreaming(): Boolean = streaming
          override fun isReliable(): Boolean = true

          override fun isCovered(file: VirtualFile): Boolean {
            calls.add(Call.here("isCovered", file))
            return false
          }

          override fun fileScanned(file: VirtualFile, text: CharSequence) {
            calls.add(Call.here("fileScanned", file))
            events?.add("scanned:${file.name}")
          }

          override fun nonIndexedScanCompleted() {
            events?.add("completed")
          }
        }
      }
    }
    FindInProjectSearchEngine.EP_NAME.point.registerExtension(engine, disposable)
    return calls
  }

  /** Registers a [DirectorySearchEngine] that wins every directory and lists it with [search]. */
  private fun registerDirectoryEngine(search: (VirtualFile, Consumer<in Collection<VirtualFile>>) -> Unit) {
    val engine = object : DirectorySearchEngine {
      override fun canSearch(findModel: FindModel): Boolean = true

      override fun canSearchNames(): Boolean = false

      override fun getWeight(directory: VirtualFile): Int = 100

      override fun searchDirectory(directory: VirtualFile, findModel: FindModel, consumer: Consumer<in Collection<VirtualFile>>) {
        search(directory, consumer)
      }

      override fun searchNames(directory: VirtualFile, pathPattern: String, consumer: Consumer<FileSearchCandidate>) {
        error("Name search is not supported.")
      }
    }
    DirectorySearchEngine.EP_NAME.point.registerExtension(engine, disposable)
  }

  /** The [files], counting in [taken] each element the walk takes. */
  private class CountingList(private val files: List<VirtualFile>, private val taken: AtomicInteger) : AbstractList<VirtualFile>() {
    override val size: Int get() = files.size

    override fun get(index: Int): VirtualFile {
      taken.incrementAndGet()
      return files[index]
    }
  }

  /** Spins until the read action of this worker is canceled; a pending write action cancels it. */
  private fun waitForCancellation() {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
    while (System.nanoTime() < deadline) {
      ProgressManager.checkCanceled()
      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1))
    }
    throw AssertionError("the write action did not cancel the read action")
  }

  /** @return the files `c1..c20` in the non-indexable root, each holding the pattern `data` once */
  private fun createConcurrentFiles(): List<VirtualFile> {
    val files = (1..20).map { baseDir.newVirtualFile("non-indexable/concurrent/c$it.txt", "concurrent file $it with data".toByteArray()) }
    VfsTestUtil.syncRefresh()
    return files
  }

  /**
   * Runs [feed] on [FEEDER_COUNT] pool threads at once, each with [files] in its own order, and waits for all of them.
   *
   * @return what [feed] returned on each thread
   */
  private fun <T> feedFromThreads(files: List<VirtualFile>, feed: (List<VirtualFile>) -> T): List<T> {
    val ready = CountDownLatch(FEEDER_COUNT)
    val futures = (0 until FEEDER_COUNT).map { i ->
      val shift = i * files.size / FEEDER_COUNT
      val rotated = files.drop(shift) + files.take(shift)
      val order = if (i % 2 == 0) rotated else rotated.reversed()
      AppExecutorUtil.getAppExecutorService().submit(Callable {
        ready.countDown()
        check(ready.await(30, TimeUnit.SECONDS)) { "the feeders did not start together" }
        feed(order)
      })
    }
    return futures.map { it.get(30, TimeUnit.SECONDS) }
  }

  private fun projectModel(pattern: String): FindModel {
    return FindModel().apply {
      stringToFind = pattern
      isCaseSensitive = false
      isMultipleFiles = true
      isProjectScope = true
      isWithSubdirectories = true
    }
  }

  private fun directoryModel(directory: VirtualFile, pattern: String): FindModel = directoryModel(directory.path, pattern)

  private fun directoryModel(directoryPath: String, pattern: String): FindModel {
    return FindModel().apply {
      stringToFind = pattern
      isCaseSensitive = false
      isMultipleFiles = true
      isProjectScope = false
      directoryName = directoryPath
      isWithSubdirectories = true
    }
  }

  private fun findUsages(model: FindModel, indicator: ProgressIndicator? = null, usageProcessor: Processor<UsageInfo>) {
    val presentation = FindInProjectUtil.setupProcessPresentation(false, FindInProjectUtil.setupViewPresentation(false, model))
    val search = Runnable { FindInProjectUtil.findUsages(model, project, presentation, emptySet(), usageProcessor) }
    if (indicator == null) {
      search.run()
    }
    else {
      ProgressManager.getInstance().runProcess(search, indicator)
    }
  }
}
