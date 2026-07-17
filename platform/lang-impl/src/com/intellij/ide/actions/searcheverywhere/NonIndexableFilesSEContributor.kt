// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.GotoActionBase
import com.intellij.ide.actions.GotoFileItemProvider
import com.intellij.ide.actions.SearchEverywherePsiRenderer
import com.intellij.ide.actions.searcheverywhere.footer.createPsiExtendedInfo
import com.intellij.ide.util.gotoByName.FileTypeRef
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.readActionUndispatched
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.IncorrectOperationException
import com.intellij.util.Processor
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.indexing.NonIndexableFilesDequeImpl
import com.intellij.util.indexing.nonIndexableRootsAsCacheAvoiding
import com.intellij.util.text.matching.MatchingMode
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileIndexEx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.ListCellRenderer
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Marker interface, indicating that this contributor will be added to the "Files" tab in Search Everywhere
 */
@ApiStatus.Internal
interface FilesTabSEContributor {
  val psiContext: SmartPsiElementPointer<PsiElement?>?
  val project: Project

  fun setScope(scope: ScopeDescriptor)
  fun setHiddenTypes(hiddenTypes: List<FileTypeRef>)

  companion object {
    @ApiStatus.Internal
    @JvmStatic
    fun SearchEverywhereContributor<*>.isFilesTabContributor(): Boolean = unwrapFilesTabContributorIfPossible() != null

    @ApiStatus.Internal
    @JvmStatic
    fun SearchEverywhereContributor<*>.unwrapFilesTabContributorIfPossible(): FilesTabSEContributor? =
      when (this) {
        is FilesTabSEContributor -> this
        is SearchEverywhereContributorWrapper -> this.getEffectiveContributor().unwrapFilesTabContributorIfPossible()
        else -> null
      }

    @ApiStatus.Internal
    @JvmStatic
    fun SearchEverywhereContributor<*>.isMainFilesContributor(): Boolean {
      return asMainFilesContributorOrNull() != null
    }

    @ApiStatus.Internal
    @JvmStatic
    fun SearchEverywhereContributor<*>.asMainFilesContributorOrNull(): FileSearchEverywhereContributor? {
      return this as? FileSearchEverywhereContributor
             ?: (this as? SearchEverywhereContributorWrapper)?.getEffectiveContributor()?.asMainFilesContributorOrNull()
    }
  }
}

private val LOG = Logger.getInstance(NonIndexableFilesSEContributor::class.java)

private class PairProgressIndicator(val delegateMain: ProgressIndicator, val delegateSubordinate: ProgressIndicator) : AbstractProgressIndicatorBase() {

  override fun start() { throw IncorrectOperationException("Should not be started") }
  override fun stop() { throw IncorrectOperationException("Should not be stoped") }

  override fun isCanceled(): Boolean { return delegateMain.isCanceled || delegateSubordinate.isCanceled }
  override fun cancel() { throw IncorrectOperationException("Should not be cancelled") }
  override fun checkCanceled() {
    delegateMain.checkCanceled()
    delegateSubordinate.checkCanceled()
  }

  override fun isRunning(): Boolean { return delegateMain.isRunning || delegateSubordinate.isRunning }
}

@ApiStatus.Internal
@OptIn(ExperimentalAtomicApi::class)
class NonIndexableFilesSEContributor(event: AnActionEvent) : WeightedSearchEverywhereContributor<Any>,
                                                             DumbAware,
                                                             FilesTabSEContributor,
                                                             SearchEverywhereExtendedInfoProvider {
  override val project: Project = event.project!!
  private val navigationHandler: SearchEverywhereNavigationHandler = FileSearchEverywhereNavigationContributionHandler(project)

  private val MAX_JOBS = 3

  private val scope: AtomicReference<ScopeDescriptor?> = AtomicReference(null)
  private val hiddenTypes: AtomicReference<Set<FileTypeRef>> = AtomicReference(emptySet())
  override val psiContext: SmartPsiElementPointer<PsiElement?>? = GotoActionBase.getPsiContext(event)?.let { context ->
    SmartPointerManager.getInstance(project).createSmartPsiElementPointer(context)
  }

  override fun getSearchProviderId(): String = ID

  override fun getGroupName(): @Nls String {
    return IdeBundle.message("search.everywhere.group.name.non.indexable.files")
  }

  override fun getSortWeight(): Int {
    return 1000
  }

  override fun showInFindResults(): Boolean = true

  override fun isShownInSeparateTab(): Boolean = false

  override fun processSelectedItem(selected: Any, modifiers: Int, searchText: String): Boolean {
    if (selected !is PsiElement) return false

    navigationHandler.gotoSelectedItem(selected, modifiers, searchText)
    return true
  }

  override fun getElementsRenderer(): ListCellRenderer<in Any> {
    return SearchEverywherePsiRenderer(this)
  }

  override fun isDumbAware(): Boolean {
    return true
  }

  override fun setScope(scope: ScopeDescriptor) {
    this.scope.store(scope)
  }

  override fun setHiddenTypes(hiddenTypes: List<FileTypeRef>) {
    this.hiddenTypes.store(hiddenTypes.toSet())
  }

  /**
   * @implNote instead of running under one large read action, launches many short blocking RAs
   */
  override fun fetchWeightedElements(
    pattern: String,
    progressIndicator: ProgressIndicator,
    consumer: Processor<in FoundItemDescriptor<Any>>,
  ) {
    ThreadingAssertions.assertBackgroundThread()
    ThreadingAssertions.assertNoReadAccess()

    if (!Registry.`is`("se.enable.non.indexable.files.contributor")) return
    if (pattern.isEmpty()) return

    val (namePattern, pathPattern) = run {
      val sanitizedPattern = GotoFileItemProvider.getSanitizedPattern(pattern, null).removePrefix("*")
      sanitizedPattern.substringAfterLast('/').removePrefix("*") to sanitizedPattern
    }

    val pathMatcher = GotoFileItemProvider.getQualifiedNameMatcher(pathPattern)
    val nameMatcher = NameUtil.buildMatcher("*$namePattern")
      .withMatchingMode(MatchingMode.IGNORE_CASE)
      .preferringStartMatches()
      .build()

    // search everywhere has limit of entries it allows contibutor to contribute.
    // We want to send good matches first, and only send others later if didn't find enough
    val suboptimalMatches = ConcurrentLinkedQueue<VirtualFile>()

    val hiddenTypes = hiddenTypes.load()
    val filterByType = VirtualFileFilter { file -> FileTypeRef.forFileType(file.fileType) !in hiddenTypes }

    val scope = scope.load()?.scope
    val filter: VirtualFileFilter = if (scope == null) filterByType
    else filterByType.and { file -> scope.containsNonIndexed(file) }

    val searchInLibraries = (scope as? GlobalSearchScope)?.isSearchInLibraries ?: true
    val workspaceFileIndex = WorkspaceFileIndexEx.getInstance(project)

    val psiManager = PsiManager.getInstance(project)

    val installedIndicator = ProgressManager.getInstance().getProgressIndicator()
    val wrappedIndicator = if (installedIndicator != null) {
      PairProgressIndicator(installedIndicator, progressIndicator)
    } else progressIndicator

    assert(wrappedIndicator.isRunning || wrappedIndicator.isCanceled)

    @Suppress("UsagesOfObsoleteApi") // must use it due to using the old contributors api
    ProgressManager.getInstance().executeProcessUnderProgress(
      {
        // tail position
        runBlockingCancellable {
          val roots = readAction { when {
            searchInLibraries -> workspaceFileIndex.nonIndexableRootsAsCacheAvoiding()
            else ->
              if (Registry.`is`("lookup.non.indexable.content.in.project.scope"))
                workspaceFileIndex.nonIndexableRootsAsCacheAvoiding { fileSet -> fileSet.kind.isContent }
              else
                setOf()
          } }

          if (roots.isEmpty()) return@runBlockingCancellable

          val state = SearchJobsState(roots)

          val toplevelProducerJob = launch(Dispatchers.IO.limitedParallelism(MAX_JOBS)) {
            ParallelQueueProcessor.createRunning(
              scope = this@launch, jobsNumber = MAX_JOBS, initialItems = state.roots, workerJobYieldTimeout = 50.milliseconds
            ) processor@{ handle, file ->
              if (!state.visitingRootAllowed(file)) return@processor
              val searchValidFile = state.processFileSubtree(file, workspaceFileIndex, handle)
              if (searchValidFile == null) return@processor

              val filePath = searchValidFile.path
              val rootOfFile = state.getPathRootOfPath(filePath)
              if (rootOfFile == null) {
                LOG.warn("File $searchValidFile that was yielded as a file under a non-indexable root didn't match any non-indexable roots; Continue search...")
                return@processor
              }

              val pathFromNonIndexableRoot = filePath.substring(rootOfFile.lastIndexOf("/") + 1)

              if (!pathMatcher.matches(pathFromNonIndexableRoot)) {
                return@processor // file doesn't match pattern, skip
              }

              val matchingDegree = nameMatcher.matchingDegree(file.name)
              if (matchingDegree <= 0) {
                suboptimalMatches.add(file)
                return@processor // suboptimal match, process later, after "optimal" matches
              }

              state.emitResult(file, matchingDegree)
            }
          }

          toplevelProducerJob.invokeOnCompletion {
            state.producerCompleted()
          }

          state.collectResults { file, matchingDegree ->
            val psiItem = when {
              file.isDirectory -> psiManager.findDirectory(file)
              else -> psiManager.findFile(file)
            }

            val accepted = filter.accept(file)
            if (!accepted) return@collectResults

            val itemDescriptor = FoundItemDescriptor<Any>(psiItem, matchingDegree)
            val consumed = consumer.process(itemDescriptor)

            if (!consumed) {
              toplevelProducerJob.cancel("consumer stopped")
            }
          }

          if (suboptimalMatches.isEmpty() || namePattern.length < 2) return@runBlockingCancellable

          val otherNameMatchers = List(namePattern.length - 1) { i ->
            NameUtil.buildMatcher(" " + namePattern.substring(i + 1))
              .withMatchingMode(MatchingMode.IGNORE_CASE)
              .build()
          }

          for (file in suboptimalMatches) {
            // binary search instead of linear?
            for (i in otherNameMatchers.indices) {
              val matcher = otherNameMatchers[i]
              val matchingDegree = matcher.matchingDegree(file.name)
              if (matchingDegree > 0) {
                // These locks slow the throughput less than the locks in the producerJob iteration
                // because these are really just file leftovers
                val psiItem = when {
                  file.isDirectory -> readActionUndispatched { PsiManager.getInstance(project).findDirectory(file) }
                  else -> readActionUndispatched { PsiManager.getInstance(project).findFile(file) }
                } ?: continue
                val weight = matchingDegree * (otherNameMatchers.size - i) / (otherNameMatchers.size + 1)
                val itemDescriptor = FoundItemDescriptor<Any>(psiItem, weight)
                if (!consumer.process(itemDescriptor)) return@runBlockingCancellable
                break
              }
            }
          }
        }
      }, wrappedIndicator
    )
  }

  override fun createExtendedInfo(): @Nls ExtendedInfo = createPsiExtendedInfo(fallbackToContentFileSetRoot = true)

  companion object {
    @ApiStatus.Internal
    const val ID: String = "NonIndexableFilesSEContributor"
  }

  @ApiStatus.Internal
  class Factory : SearchEverywhereContributorFactory<Any?> {
    override fun createContributor(initEvent: AnActionEvent): SearchEverywhereContributor<Any?> {
      return PSIPresentationBgRendererWrapper.wrapIfNecessary(NonIndexableFilesSEContributor(initEvent))
    }

    override fun isAvailable(project: Project?): Boolean {
      return project != null && Registry.`is`("se.enable.non.indexable.files.contributor")
    }
  }
}

private class SearchJobsState {
  val roots: Set<VirtualFile>
  private val rootsPaths: List<String>
  private val resultsChannel: Channel<Pair<VirtualFile, Int>> = Channel(Channel.UNLIMITED)

  val visitedRoots: MutableSet<VirtualFile> = ConcurrentHashMap.newKeySet()

  constructor(roots: Set<VirtualFile>) {
    this.roots = ConcurrentHashMap.newKeySet<VirtualFile>().apply { addAll(roots) }
    this.rootsPaths = roots.map { it.path }
  }

  fun getPathRootOfPath(filePath: String): String? {
    return rootsPaths.firstOrNull { filePath.startsWith(it) }
  }

  fun visitingRootAllowed(file: VirtualFile): Boolean {
    if (file in visitedRoots) return false
    if (file in roots) visitedRoots.add(file)
    return true
  }

  fun processFileSubtree(
    file: VirtualFile, workspaceFileIndex: WorkspaceFileIndexEx,
    processorHandle: ParallelQueueProcessor<VirtualFile>
  ): VirtualFile? {
    val shouldProcess = NonIndexableFilesDequeImpl.shouldProcessFileAndListChildrenIfTheyShouldBe(workspaceFileIndex, file) { children ->
      for (child in children) {
        processorHandle.queueSpawningWorkerJobIfNotAtLimit(child)
      }
    }
    return if (shouldProcess) file else null
  }

  fun emitResult(file: VirtualFile, score: Int) {
    resultsChannel.trySend(file to score)
  }

  suspend fun collectResults(@RequiresReadLock collector: (VirtualFile, Int) -> Unit) {
    for ((file, score) in resultsChannel) {
      // Sadly, read action :( SE is to blame
      readActionUndispatched { collector(file, score) }
    }
  }

  fun producerCompleted() {
    resultsChannel.close()
  }
}

// Implementation note:
// this thing is optimized for speed of iteration, not
private class ParallelQueueProcessor<TNode : Any> private constructor(
  val parentScope: CoroutineScope,
  val jobsNumber: Int,
  val queue: ConcurrentLinkedQueue<TNode>,
  val jobYieldTimeout: Long,
  val processorToBeParalleled: suspend (ParallelQueueProcessor<TNode>, TNode) -> Unit,
) {

  companion object {
    @JvmStatic
    fun <TNode : Any> createRunning(
      scope: CoroutineScope,
      jobsNumber: Int,
      initialItems: Collection<TNode>,
      workerJobYieldTimeout: Duration,
      processor: suspend (ParallelQueueProcessor<TNode>, TNode) -> Unit
    ): ParallelQueueProcessor<TNode> {
      return ParallelQueueProcessor(
        scope, jobsNumber, ConcurrentLinkedQueue<TNode>(), workerJobYieldTimeout.inWholeNanoseconds, processor
      ).apply {
        for (it in initialItems) {
          queueSpawningWorkerJobIfNotAtLimit(it)
        }
      }
    }
  }

  // This has to be lockfree-threadsafe because
  // (a) we add jobs dynamically
  // (b) the addition of jobs should be able to succeed in parallel without locking java thread sync (it is a JVM memory barrier, alas!)
  // because it happens in a hot path during iteration of files
  private val jobsRunning = AtomicInteger(0)
  private fun tryAcquirePermit(): Boolean {
    val alreadyRunningCount = jobsRunning.get()
    if (alreadyRunningCount >= jobsNumber) {
      return false
    }

    val incrementedCount = jobsRunning.incrementAndGet()
    if (incrementedCount <= jobsNumber) {
      return true
    }

    // lockfree shennanigans: we must handle every possible value of count here because it can be any value
    // this mechanism guarantees eventual de-contention
    // Consider:
    // 5 jobs are trying to start, and limit is 3;
    // all 5 have incremented the jobsRunning counter
    // then 3 have passed the increment check
    // two are left (incrementedCount == 4, incrementedCount == 5), jobsRunning is 5
    // every one of two needs to decrement
    // one of those will get decrementedCount == 4, another -- decrementedCount == 5
    // both should fail-out
    // BUT it could be that they are placed in contention with completion of other jobs
    // (that also decrements the counter)
    // hence we need to have the count be checked for updates...
    var decrementedCount = jobsRunning.decrementAndGet()
    while (true) {
      if (decrementedCount > jobsNumber) {
        return false
      }
      // Here we overshot the decrement due to contention, so it is like decrementedCount == 1
      // We need to get it back, but iff no-one contended or contended in an ABA manner
      // (this aquired the permit for the current job)
      val actualCount = jobsRunning.compareAndExchange(decrementedCount, decrementedCount + 1)
      if (actualCount == decrementedCount) {
        return true
      }

      // contention: loop again
      decrementedCount = actualCount
    }
  }

  private fun releasePermit() {
    jobsRunning.decrementAndGet()
  }

  private fun launchProcessingJob() {
    parentScope.launch(start = CoroutineStart.UNDISPATCHED) {
      val currentJob = coroutineContext.job
      do {
        if (!tryAcquirePermit()) return@launch
        try {
          // Drop the start == UNDISPATCHED status
          yield()
          // Pass the job to avoid the linked list access on coroutineContext.job
          dequeueAndProcess(currentJob)
        }
        catch (@Suppress("IncorrectCancellationExceptionHandling") e: CancellationException) {
          // This prohibits the "internal" cancellation of this job via a cancellation thrown by client code
          // The Exception propagates properly to the toplevel and dismantles the entire parent scope.
          // Must be a common idiom in platform code ;(
          currentJob.ensureActive()
          throw Exception("Unexpected cancellation while processing!", e)
        }
        finally {
          releasePermit()
        }
      // This *does* make it so that some jobs complete even though
      // some other still might queue more work items onto the queue.
      // That's why the queue method relaunches jobs up to maxJobsNumber
      // THE INVARIANT is that there is always at least ONE job doing work
      } while (queue.isNotEmpty())
    }
  }

  private suspend fun dequeueAndProcess(currentJob: Job) {
    var start = System.nanoTime()
    while (true) {
      val item = queue.poll() ?: return

      currentJob.ensureActive()
      // Ideologically it should pass a separate object here, but this is a hot path
      // and allocs are unwanted here
      processorToBeParalleled(this@ParallelQueueProcessor, item)

      // It is paramount that this exists when we are queued to some shared fixed-sized
      // thread-pool-backed Dispatcher; like Dispatchers.Default or Dispatchers.IO
      // Dispatchers.IO.limitedParallelism dispatchers are safe without it -- because
      // "limited" isn't really "limited" for them. read the KDoc for Dispatchers.IO.
      if (System.nanoTime() - start >= jobYieldTimeout) {
        yield()
        start = System.nanoTime()
      }
    }
  }

  fun queueSpawningWorkerJobIfNotAtLimit(node: TNode) {
    queue.add(node)

    if (jobsRunning.get() < jobsNumber) {
      // This should be enough to keep the throughput maxed-out
      launchProcessingJob()
    }
  }
}
