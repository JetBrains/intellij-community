// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find

import com.intellij.find.FindInProjectSearchEngine.Coverage
import com.intellij.find.FindInProjectSearchEngine.FindInProjectSearcher
import com.intellij.find.FindInProjectSearchEngine.ScanObserver
import com.intellij.find.impl.CandidateFilter
import com.intellij.find.impl.FindInProjectUtil
import com.intellij.find.impl.ScopeWalker
import com.intellij.find.impl.WorkItem
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.testFramework.IndexingTestUtil.Companion.waitUntilIndexesAreReady
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.rules.ProjectModelExtension
import com.intellij.testFramework.LightVirtualFile
import com.intellij.usageView.UsageInfo
import com.intellij.usages.FindUsagesProcessPresentation
import com.intellij.util.Processor
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.indexing.ConcurrentFileTraversal
import com.intellij.util.indexing.SubtreeProcessingMode
import com.intellij.util.indexing.testEntities.NonIndexableKindFileSetTestContributor
import com.intellij.util.indexing.testEntities.NonIndexableTestEntity
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexImpl
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.Collections.synchronizedList
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pins the phases of the `FindInProjectTask` candidate pipeline through [FindInProjectUtil.findUsages]:
 * the priority files drain before the searchers run, the searcher files drain before the walk loads a file,
 * a write action loses no file, a searcher error ends the search, and the intended behavior deltas of the rewrite.
 * The files `s1..s3` (searcher files) and `w1..w3` (walk files) live in a non-indexable root, so the walk finds all of them;
 * each holds the pattern `data` once.
 */
@TestApplication
@RegistryKey("find.in.files.in.non.indexable.enable", "true")
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class FindInProjectPipelineTest {
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

  @Test
  fun `a previous-search file is scanned before any searcher runs`(): Unit = runBlocking {
    val module = projectModel.createModule()
    PsiTestUtil.addContentRoot(module, baseDir.newVirtualDirectory("content"))
    val priorityFile = baseDir.newVirtualFile("content/priority.txt", "priority file with data".toByteArray())
    VfsTestUtil.syncRefresh()
    waitUntilIndexesAreReady(project)

    val events = Events()
    registerCollecting { events.add("collect"); searcherFiles }
    registerStreaming { processor -> events.add("stream"); processor.process(walkFiles[0]) }

    findUsages(projectModel("data"), filesToScanInitially = setOf(priorityFile)) { usage ->
      events.add("usage:${usage.virtualFile!!.name}")
      true
    }

    val log = events.snapshot()
    assertThat(log).contains("usage:priority.txt", "collect", "stream")
    assertThat(log.indexOf("usage:priority.txt")).isLessThan(log.indexOf("collect"))
    assertThat(log.indexOf("usage:priority.txt")).isLessThan(log.indexOf("stream"))
  }

  @Test
  fun `a collected file is scanned while a streaming searcher still streams`() {
    val collectedUsage = CountDownLatch(1)
    val collectedBeforeStream = AtomicBoolean()
    registerCollecting { listOf(searcherFiles[0]) }
    registerStreaming { processor ->
      collectedBeforeStream.set(collectedUsage.await(30, TimeUnit.SECONDS))
      processor.process(searcherFiles[1])
    }

    val usages = synchronizedList(ArrayList<String>())
    findUsages(projectModel("data")) { usage ->
      usages.add(usage.virtualFile!!.name)
      if (usage.virtualFile == searcherFiles[0]) collectedUsage.countDown()
      true
    }

    assertThat(collectedBeforeStream.get()).isTrue()
    assertThat(usages).containsExactlyInAnyOrder("s1.txt", "s2.txt", "s3.txt", "w1.txt", "w2.txt", "w3.txt")
  }

  @Test
  fun `every searcher file is scanned before the walk loads its first file`() {
    val events = Events()
    registerCollecting(events) { searcherFiles.take(2) }
    registerStreaming { processor -> processor.process(searcherFiles[2]) }

    findUsages(projectModel("data")) { true }

    val scanned = events.snapshot().filter { it.startsWith("scanned:") }.map { it.removePrefix("scanned:") }
    val searcherNames = searcherFiles.map { it.name }
    val walkNames = walkFiles.map { it.name }
    assertThat(scanned).containsExactlyInAnyOrderElementsOf(searcherNames + walkNames)
    val lastSearcherScan = scanned.indexOfLast { it in searcherNames }
    val firstWalkScan = scanned.indexOfFirst { it in walkNames }
    assertThat(lastSearcherScan).isLessThan(firstWalkScan)
  }

  @Test
  fun `a write action in the middle of a scan loses no file and reports none twice`() {
    registerCollecting { searcherFiles }
    val interruptAt = setOf("s1.txt", "w1.txt")
    val interrupted = synchronizedList(ArrayList<String>())
    val writeActions = synchronizedList(ArrayList<CompletableFuture<Void>>())

    val usages = synchronizedList(ArrayList<String>())
    findUsages(projectModel("data")) { usage ->
      val name = usage.virtualFile!!.name
      if (name in interruptAt && !interrupted.contains(name)) {
        interrupted.add(name)
        writeActions.add(CompletableFuture.runAsync({ WriteAction.runAndWait<RuntimeException> {} },
                                                    AppExecutorUtil.getAppExecutorService()))
        waitForCancellation() // the pending write action cancels this worker's read action; the pool retries the file
      }
      usages.add(name)
      true
    }

    writeActions.forEach { it.get(30, TimeUnit.SECONDS) }
    assertThat(interrupted).containsExactlyInAnyOrderElementsOf(interruptAt)
    assertThat(usages).containsExactlyInAnyOrder("s1.txt", "s2.txt", "s3.txt", "w1.txt", "w2.txt", "w3.txt")
  }

  @Test
  fun `a write action after delivered usages re-scans the file and reports each usage once`() {
    baseDir.newVirtualFile("non-indexable/multi.txt", (1..5).joinToString("\n") { "line $it with data" }.toByteArray())
    VfsTestUtil.syncRefresh()
    val multiCalls = AtomicInteger()
    val interrupted = AtomicBoolean()
    val writeActions = synchronizedList(ArrayList<CompletableFuture<Void>>())

    val usages = synchronizedList(ArrayList<String>())
    findUsages(projectModel("data")) { usage ->
      val name = usage.virtualFile!!.name
      // the 3rd usage of multi.txt is interrupted before it is recorded; the 1st and 2nd were delivered already
      if (name == "multi.txt" && multiCalls.incrementAndGet() == 3 && interrupted.compareAndSet(false, true)) {
        writeActions.add(CompletableFuture.runAsync({ WriteAction.runAndWait<RuntimeException> {} },
                                                    AppExecutorUtil.getAppExecutorService()))
        waitForCancellation()
      }
      usages.add(name)
      true
    }

    writeActions.forEach { it.get(30, TimeUnit.SECONDS) }
    assertThat(interrupted.get()).isTrue()
    assertThat(usages.filter { it == "multi.txt" }).describedAs("usages of multi.txt in $usages").hasSize(5)
    assertThat(usages.filter { it != "multi.txt" }).containsExactlyInAnyOrder("s1.txt", "s2.txt", "s3.txt",
                                                                              "w1.txt", "w2.txt", "w3.txt")
  }

  @Test
  fun `a ProcessCanceledException from fileScanned retries the file once the hook stops throwing`() {
    val events = Events()
    val hookFile = walkFiles[0]
    val thrown = AtomicBoolean()
    registerSearcher(events, streaming = false, search = { true }, scanned = { file ->
      if (file == hookFile && thrown.compareAndSet(false, true)) throw ProcessCanceledException()
    })

    findUsages(projectModel("data")) { usage ->
      events.add("usage:${usage.virtualFile!!.name}")
      true
    }

    val log = events.snapshot()
    assertThat(thrown.get()).isTrue()
    assertThat(log.filter { it == "scanned:${hookFile.name}" }).describedAs("events $log").hasSize(2)
    assertThat(log.filter { it == "usage:${hookFile.name}" }).describedAs("events $log").hasSize(1)
    assertThat(log).contains("completed")
  }

  @Test
  fun `a stop on a priority file ends the search before any searcher runs`(): Unit = runBlocking {
    val module = projectModel.createModule()
    PsiTestUtil.addContentRoot(module, baseDir.newVirtualDirectory("content"))
    val priorityFile = baseDir.newVirtualFile("content/priority.txt", "priority file with data".toByteArray())
    VfsTestUtil.syncRefresh()
    waitUntilIndexesAreReady(project)

    val events = Events()
    registerCollecting(events) { events.add("collect"); searcherFiles }
    registerStreaming(events) { processor -> events.add("stream"); processor.process(walkFiles[0]) }

    findUsages(projectModel("data"), filesToScanInitially = setOf(priorityFile)) { usage ->
      events.add("usage:${usage.virtualFile!!.name}")
      false
    }

    val log = events.snapshot()
    assertThat(log).contains("usage:priority.txt")
    assertThat(log).doesNotContain("collect", "stream", "completed")
  }

  @Test
  fun `a collecting searcher error is logged and ends the search before the walk`() {
    val events = Events()
    val boom = IllegalStateException("collecting searcher failed")
    registerCollecting(events) { throw boom }

    val usages = synchronizedList(ArrayList<String>())
    val logged = LoggedErrorProcessor.executeAndReturnLoggedError {
      findUsages(projectModel("data")) { usages.add(it.virtualFile!!.name) }
    }

    assertThat(logged).isSameAs(boom)
    assertThat(events.snapshot()).doesNotContain("completed")
    assertThat(usages).isEmpty()
  }

  @Test
  fun `a streaming searcher error is logged and ends the search before the walk`() {
    val events = Events()
    val boom = IllegalStateException("streaming searcher failed")
    registerStreaming(events) { processor ->
      processor.process(searcherFiles[0])
      throw boom
    }

    val usages = synchronizedList(ArrayList<String>())
    val logged = LoggedErrorProcessor.executeAndReturnLoggedError {
      findUsages(projectModel("data")) { usages.add(it.virtualFile!!.name) }
    }

    assertThat(logged).isSameAs(boom)
    assertThat(events.snapshot()).doesNotContain("completed")
    assertThat(usages).doesNotContainAnyElementsOf(walkFiles.map { it.name })
  }

  @Test
  fun `a searcher file under an excluded directory is not reported in a directory search`(): Unit = runBlocking {
    val module = projectModel.createModule()
    val root = baseDir.newVirtualDirectory("root")
    PsiTestUtil.addContentRoot(module, root)
    PsiTestUtil.addExcludedRoot(module, baseDir.newVirtualDirectory("root/excluded"))
    baseDir.newVirtualFile("root/kept.txt", "kept file with data".toByteArray())
    val hiddenFile = baseDir.newVirtualFile("root/excluded/hidden.txt", "hidden file with data".toByteArray())
    VfsTestUtil.syncRefresh()
    waitUntilIndexesAreReady(project)
    registerCollecting { listOf(hiddenFile) }
    registerStreaming { processor -> processor.process(hiddenFile) }

    val usages = synchronizedList(ArrayList<String>())
    findUsages(directoryModel(root, "data")) { usages.add(it.virtualFile!!.name) }

    assertThat(usages).containsExactly("kept.txt")
  }

  @Test
  fun `FindModelExtension files pass the mask and are reported once`() {
    val shared = baseDir.newVirtualFile("extension/shared.txt", "shared extension file with data".toByteArray())
    val repeated = baseDir.newVirtualFile("extension/repeated.txt", "repeated extension file with data".toByteArray())
    val masked = baseDir.newVirtualFile("extension/masked.md", "masked extension file with data".toByteArray())
    FindModelExtension.EP_NAME.point.registerExtension(
      FindModelExtension { _, _, consumer ->
        consumer.process(shared) && consumer.process(repeated) && consumer.process(masked) && consumer.process(repeated)
      },
      disposable)
    registerCollecting { listOf(shared) }

    val usages = synchronizedList(ArrayList<String>())
    findUsages(projectModel("data", fileMask = "*.txt")) { usages.add(it.virtualFile!!.name) }

    assertThat(usages).containsExactlyInAnyOrder("shared.txt", "repeated.txt",
                                                 "s1.txt", "s2.txt", "s3.txt", "w1.txt", "w2.txt", "w3.txt")
  }

  @Test
  fun `the progress fraction covers the walk and ends at 1`() {
    val events = Events()
    val onlyWalkFile = walkFiles[0]
    registerCollecting { searcherFiles + walkFiles.drop(1) }
    val indicator = object : ProgressIndicatorBase() {
      override fun setFraction(fraction: Double) {
        super.setFraction(fraction)
        events.add("fraction:$fraction")
      }
    }

    findUsages(projectModel("data"), indicator = indicator) { usage ->
      if (usage.virtualFile == onlyWalkFile) {
        events.add("usage:walk")
      }
      true
    }

    val log = events.snapshot()
    val fractions = log.filter { it.startsWith("fraction:") }.map { it.removePrefix("fraction:").toDouble() }
    assertThat(fractions).allSatisfy { assertThat(it).isBetween(0.0, 1.0) }
    assertThat(log.indexOf("usage:walk")).isNotNegative()
    assertThat(log.drop(log.indexOf("usage:walk")).filter { it.startsWith("fraction:") }).isNotEmpty()
    assertThat(fractions.last()).isEqualTo(1.0)
  }

  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  fun `a plain CancellationException from a scan hook ends the search`() {
    val hookFile = walkFiles[0]
    registerSearcher(events = null, streaming = false, search = { true }, scanned = { file ->
      if (file == hookFile) throw CancellationException("plain cancellation from fileScanned")
    })

    val model = projectModel("data")
    val presentation = presentationOf(model)
    val indicator = ProgressIndicatorBase()
    val search = CompletableFuture.runAsync(
      { findUsages(model, indicator = indicator, presentation = presentation) { true } },
      AppExecutorUtil.getAppExecutorService())

    // the queue wraps the cancellation, and findUsages rethrows it; it must not wait forever for the item the pool dropped
    val outcome = try {
      search.get(10, TimeUnit.SECONDS)
      "returned"
    }
    catch (_: CancellationException) {
      "threw"
    }
    catch (e: ExecutionException) {
      assertThat(e.cause).isInstanceOf(CancellationException::class.java)
      "threw"
    }
    catch (_: TimeoutException) {
      indicator.cancel() // unblock the hung drain, so the test ends and leaves no worker behind
      runCatching { search.get(10, TimeUnit.SECONDS) }
      "hung"
    }
    assertThat(outcome).describedAs("findUsages after a plain CancellationException in fileScanned").isEqualTo("threw")
    assertThat(presentation.isCanceled).isTrue()
  }

  @Test
  fun `a walk file covered by a searcher is still reported through a FindModelExtension`() {
    val shared = walkFiles[0]
    val walkChecking = CountDownLatch(1)
    val extensionOffered = CountDownLatch(1)
    val handshake = AtomicBoolean(true)
    // the extension offers the file after the walk offered it and before the walk's worker check rejects it (covered);
    // each side waits on a latch with a timeout, so a single worker thread cannot deadlock (the test is skipped then)
    FindModelExtension.EP_NAME.point.registerExtension(
      FindModelExtension { _, _, consumer ->
        if (!walkChecking.await(10, TimeUnit.SECONDS)) handshake.set(false)
        val result = consumer.process(shared)
        extensionOffered.countDown()
        result
      },
      disposable)
    registerSearcher(events = null, streaming = false, search = { true }, covered = { file ->
      file == shared && run {
        walkChecking.countDown()
        if (!extensionOffered.await(10, TimeUnit.SECONDS)) handshake.set(false)
        true
      }
    })

    val usages = synchronizedList(ArrayList<String>())
    findUsages(projectModel("data")) { usages.add(it.virtualFile!!.name) }

    assumeTrue(handshake.get(), "the walk check and the extension did not run on two workers at once")
    assertThat(usages).contains(shared.name)
  }

  /**
   * A stop while a non-indexable directory hands its children: the files after the stop get no WorkspaceFileIndex lookup.
   * The traversal skips each file (an indexed file), so no candidate offer returns false and tells the walker about the stop.
   */
  @Test
  fun `a stop while a traversal directory is listed makes no lookup for the next file`() {
    val sink = StoppableSink()
    val lookupsAfterStop = AtomicInteger()
    val children = (1..20).map { i ->
      FakeTraversalItem(LightVirtualFile("f$i.txt"), lookup = {
        if (sink.stopped) lookupsAfterStop.incrementAndGet()
        sink.stopped = true // the search stops while the first file is looked up
      })
    }
    val directory = FakeTraversalItem(LightVirtualFile("dir"), children)
    val candidateFilter = CandidateFilter({ true }, { false }, { true }, { true }, { false }, false, { null })
    val walker = ScopeWalker(projectModel("data"), project, candidateFilter, null, null, false, false, null)

    walker.expand(ScopeWalker.WalkPlan(emptyList(), false, emptyList(), true), WorkItem.Walk.Traversal(directory), sink)

    assertThat(lookupsAfterStop.get()).isZero()
  }

  /** Spins until the read action of this worker is canceled; a pending write action cancels it. */
  private fun waitForCancellation() {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
    while (System.nanoTime() < deadline) {
      ProgressManager.checkCanceled()
      Thread.sleep(1)
    }
    throw AssertionError("the write action did not cancel the read action")
  }

  /** The events of one test in arrival order, from any thread. */
  private class Events {
    private val list = synchronizedList(ArrayList<String>())

    fun add(event: String) {
      list.add(event)
    }

    fun snapshot(): List<String> = synchronized(list) { ArrayList(list) }
  }

  /** Registers a reliable collecting searcher that covers no file; [events] records `scanned:<name>` and `completed`. */
  private fun registerCollecting(events: Events? = null, collect: () -> Collection<VirtualFile>) {
    registerSearcher(events, streaming = false, search = { processor -> collect().all { processor.process(it) } })
  }

  /** Registers a reliable streaming searcher that covers no file; [events] records `scanned:<name>` and `completed`. */
  private fun registerStreaming(events: Events? = null, stream: (Processor<in VirtualFile>) -> Boolean) {
    registerSearcher(events, streaming = true, search = stream)
  }

  private fun registerSearcher(events: Events?,
                               streaming: Boolean,
                               search: (Processor<in VirtualFile>) -> Boolean,
                               covered: (VirtualFile) -> Boolean = { false },
                               scanned: (VirtualFile) -> Unit = {}) {
    val engine = object : FindInProjectSearchEngine {
      override fun createSearcher(findModel: FindModel, project: Project): FindInProjectSearcher {
        return object : ScanObserver {
          override fun searchForOccurrences(): Collection<VirtualFile> {
            check(!streaming) { "a streaming searcher is not asked to collect" }
            val files = ArrayList<VirtualFile>()
            search(Processor { files.add(it) })
            return files
          }
          override fun processOccurrences(processor: Processor<in VirtualFile>): Coverage = when {
            !streaming -> super.processOccurrences(processor)
            search(processor) -> Coverage.INDEXED_ONLY
            else -> Coverage.STOPPED
          }
          override fun isStreaming(): Boolean = streaming
          override fun isReliable(): Boolean = true
          override fun isCovered(file: VirtualFile): Boolean = covered(file)
          override fun fileScanned(file: VirtualFile, text: CharSequence) {
            events?.add("scanned:${file.name}")
            scanned(file)
          }
          override fun nonIndexedScanCompleted() {
            events?.add("completed")
          }
        }
      }
    }
    FindInProjectSearchEngine.EP_NAME.point.registerExtension(engine, disposable)
  }

  private fun projectModel(pattern: String, fileMask: String? = null): FindModel {
    return FindModel().apply {
      stringToFind = pattern
      isCaseSensitive = false
      isMultipleFiles = true
      isProjectScope = true
      isWithSubdirectories = true
      fileFilter = fileMask
    }
  }

  private fun directoryModel(directory: VirtualFile, pattern: String): FindModel {
    return FindModel().apply {
      stringToFind = pattern
      isCaseSensitive = false
      isMultipleFiles = true
      isProjectScope = false
      directoryName = directory.path
      isWithSubdirectories = true
    }
  }

  private fun presentationOf(model: FindModel): FindUsagesProcessPresentation {
    return FindInProjectUtil.setupProcessPresentation(false, FindInProjectUtil.setupViewPresentation(false, model))
  }

  /** A sink that takes nothing after [stopped] is set. */
  private class StoppableSink : ScopeWalker.Sink {
    @Volatile
    var stopped: Boolean = false

    override val isStopped: Boolean
      get() = stopped

    override fun addItem(item: WorkItem.Walk): Boolean = !stopped

    override fun addCandidate(file: VirtualFile, source: CandidateFilter.Source): Boolean = !stopped
  }

  /**
   * A traversal item of a plain file whose lookup runs [lookup] and skips the file, or of a directory that hands [children]
   * in one batch and is not processed itself.
   */
  private class FakeTraversalItem(override val file: VirtualFile,
                                  private val children: List<ConcurrentFileTraversal.TraversalItem> = emptyList(),
                                  private val lookup: () -> Unit = {}) : ConcurrentFileTraversal.TraversalItem {
    override fun getSubtreeProcessingMode(): SubtreeProcessingMode {
      lookup()
      return SubtreeProcessingMode.NONE
    }

    override fun expand(consumer: (List<ConcurrentFileTraversal.TraversalItem>) -> Unit): Boolean {
      consumer(children)
      return false
    }
  }

  private fun findUsages(model: FindModel,
                         filesToScanInitially: Set<VirtualFile> = emptySet(),
                         indicator: ProgressIndicator? = null,
                         presentation: FindUsagesProcessPresentation = presentationOf(model),
                         usageProcessor: Processor<UsageInfo>) {
    val search = Runnable { FindInProjectUtil.findUsages(model, project, presentation, filesToScanInitially, usageProcessor) }
    if (indicator == null) {
      search.run()
    }
    else {
      ProgressManager.getInstance().runProcess(search, indicator)
    }
  }
}
