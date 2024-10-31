// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.projectFilter

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.observable.util.plus
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.platform.backend.observation.Observation
import com.intellij.util.SystemProperties
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexImpl
import com.intellij.util.indexing.IndexInfrastructure
import com.intellij.util.indexing.dependencies.ProjectIndexingDependenciesService
import com.intellij.util.indexing.roots.IndexableFilesIterator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val LOG = Logger.getInstance(ProjectIndexableFilesFilterHealthCheck::class.java)

internal typealias FileId = Int
private fun FileId.fileInfo(): String = "file id=$this path=${PersistentFS.getInstance().findFileById(this)?.path}"

private class ProjectIndexableFilesFilterHealthCheckStarter : ProjectActivity {
  init {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    val delay = if (ApplicationManagerEx.isInIntegrationTest()) 1.minutes else 5.minutes
    // don't get service too early
    delay(delay)
    val healthCheck = project.serviceAsync<ProjectIndexableFilesFilterHealthCheck>()
    while (true) {
      healthCheck.launchHealthCheck()
      delay(delay)
    }
  }
}

@Service(Service.Level.PROJECT)
class ProjectIndexableFilesFilterHealthCheck(private val project: Project, private val coroutineScope: CoroutineScope) {
  private val isRunning = AtomicBoolean()
  private val attemptsCount = AtomicInteger()
  private val cancelledAttemptsCount = AtomicInteger()
  private val successfulAttemptsCount = AtomicInteger()

  fun launchHealthCheck() {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return
    }

    coroutineScope.launch {
      if (!isRunning.compareAndSet(false, true)) {
        return@launch
      }
      try {
        runHealthCheck()
      }
      finally {
        isRunning.set(false)
      }
    }
  }

  /**
   * [ProjectIndexableFilesFilter] is compared to set of files from [FileBasedIndexImpl.iterateIndexableFiles] (all files that
   * should belong to workspace)
   *
   * There could be two possible types of inconsistency between [ProjectIndexableFilesFilter] and [FileBasedIndexImpl.iterateIndexableFiles]:
   * 1. False positives - files that were found in filter but were NOT iterated by [FileBasedIndexImpl.iterateIndexableFiles].
   *    This is fine because files can be removed from workspace, but we clear indexes for them lazily
   * 2. False negatives - files that were NOT found in filter but were iterated by [FileBasedIndexImpl.iterateIndexableFiles]
   */
  private suspend fun runHealthCheck() {
    if (!IndexInfrastructure.hasIndices()) return
    val filter = (FileBasedIndex.getInstance() as? FileBasedIndexImpl)?.indexableFilesFilterHolder?.getProjectIndexableFiles(project)
                 ?: return

    LOG.runAndLogException {
      val attemptNumber = attemptsCount.incrementAndGet()
      IndexableFilesFilterHealthCheckCollector.reportIndexableFilesFilterHealthcheckStarted(project, filter, attemptNumber)
      val startTime = System.currentTimeMillis()

      Observation.awaitConfiguration(project) // wait for project import IDEA-348501
      val res: HealthCheckResult = runHealthCheck(project, filter)
      when (res) {
        is HealthCheckFinished -> {
          res.nonIndexableFilesInFilter.fix(filter)
          res.indexableFilesNotInFilter.fix(filter)

          IndexableFilesFilterHealthCheckCollector.reportIndexableFilesFilterHealthcheck(
            project,
            filter,
            attemptNumber,
            successfulAttemptsCount.incrementAndGet(),
            (System.currentTimeMillis() - startTime).toInt(),
            res.nonIndexableFilesInFilter.size,
            res.indexableFilesNotInFilter.size)

          res.nonIndexableFilesInFilter.logMessage()
          res.indexableFilesNotInFilter.logMessage()
        }
        is HealthCheckCancelled -> {
          IndexableFilesFilterHealthCheckCollector.reportIndexableFilesFilterHealthcheckCancelled(
            project,
            filter,
            attemptNumber,
            cancelledAttemptsCount.incrementAndGet(),
            (System.currentTimeMillis() - startTime).toInt(),
            res.reason)
        }
      }
    }
  }

  private suspend fun runHealthCheck(project: Project, filter: ProjectIndexableFilesFilter): HealthCheckResult {
    // We cannot use NBRA here because computation is too long and will be canceled too many times by write actions
    // Instead we cancel computation manually less often when we detected relevant change: change in filter, dumb mode or project roots
    val trackers = listOf(DumbService.getInstance(project).modificationTracker, ProjectRootModificationTracker.getInstance(project))
    val computation = object : Computation<HealthCheckResult> {
      override fun compute(checkCancelled: () -> Unit): HealthCheckResult {
        checkCancelled()
        val res =  doRunHealthCheck(project, checkCancelled, filter.checkAllExpectedIndexableFilesDuringHealthcheck, filter.getFileStatuses())
        checkCancelled()
        return res
      }
    }

    return computation
      .takeIfInSmartMode(project)
      .takeIfScanningIsCompleted(project)
      .takeIfNoChange(trackers)
      .takeIfNoChange(filter)
      .execute(delayBetweenAttempts = 10.seconds,
               maxAttempts = Registry.intValue("index.files.filter.health.check.max.attempts"),
               valueIfUnsuccessful = HealthCheckCancelled(FilterActionCancellationReason.MAX_ATTEMPTS_REACHED))
  }

  /**
   * This healthcheck makes the most sense for [com.intellij.util.indexing.projectFilter.IncrementalProjectIndexableFilesFilter]
   * because it's filled during scanning which iterates over [com.intellij.util.indexing.roots.IndexableFilesIterator]s
   * so if we iterate over IndexableFilesIterator again, it should match filter.
   * Such errors would mean that we missed some event when a file became (un)indexed e.g., when it was deleted or marked excluded.
   *
   * Errors reported for [com.intellij.util.indexing.projectFilter.CachingProjectIndexableFilesFilter] could also mean an inconsistency
   * between [com.intellij.util.indexing.roots.IndexableFilesIterator] and [com.intellij.util.indexing.IndexableFilesIndex].
   */
  private fun doRunHealthCheck(project: Project,
                               checkCancelled: () -> Unit,
                               checkAllExpectedIndexableFiles: Boolean,
                               fileStatuses: Sequence<Pair<FileId, Boolean>>): HealthCheckFinished {
    val nonIndexableFilesInFilter = mutableListOf<FileId>()
    val indexableFilesNotInFilter = mutableListOf<FileId>()

    val shouldBeIndexable = getFilesThatShouldBeIndexable(project, checkCancelled)
    val filesInFilter = BitSet()

    fileStatuses.forEachIndexed { index, (fileId, isInFilter) -> // Sequence instead of BitSet because we need to distinguish false and null
      ProgressManager.checkCanceled()
      if (index % 10000 == 0) {
        checkCancelled()
      }
      filesInFilter[fileId] = isInFilter
      if (shouldBeIndexable[fileId]) {
        if (!isInFilter) {
          indexableFilesNotInFilter.add(fileId)
        }
      }
      else if (isInFilter && !shouldBeIndexable[fileId]) {
        nonIndexableFilesInFilter.add(fileId)
      }
    }

    if (checkAllExpectedIndexableFiles) {
      for (fileId in 0 until shouldBeIndexable.size) {
        if (shouldBeIndexable[fileId] && !filesInFilter[fileId]) {
          indexableFilesNotInFilter.add(fileId)
        }
      }
    }

    return HealthCheckFinished(NonIndexableFilesInFilterGroup(nonIndexableFilesInFilter),
                               IndexableFilesNotInFilterGroup(indexableFilesNotInFilter, shouldBeIndexable))
  }

  private fun getFilesThatShouldBeIndexable(project: Project, checkCancelled: () -> Unit): IndexableFiles {
    val indexableFiles = IndexableFiles()
    getFilesThatShouldBeIndexable(project, checkCancelled, if (shouldLogProviders) IndexableFilesSetWithProvidersHandler(indexableFiles) else IndexableFilesSetHandler(indexableFiles))
    return indexableFiles
  }

  private fun <T> getFilesThatShouldBeIndexable(project: Project, checkCancelled: () -> Unit, handler: FilesSetHandler<T>) {
    val index = FileBasedIndex.getInstance() as FileBasedIndexImpl
    val providers = index.getIndexableFilesProviders(project)
    for (provider in providers) {
      val state: T = handler.createStateForProvider()
      val outerProcessor = ContentIterator {
        if (it is VirtualFileWithId) {
          ProgressManager.checkCanceled()
          handler.addToState(state, it.id)
        }
        true
      }
      checkCancelled()
      provider.iterateFiles(project, outerProcessor, VirtualFileFilter.ALL)
      handler.flushState(state, provider)
    }
  }
}

private interface FilesSetHandler<T> {
  fun createStateForProvider(): T
  fun addToState(state: T, id: FileId)
  fun flushState(state: T, iterator: IndexableFilesIterator)
}

private class IndexableFilesSetWithProvidersHandler(val indexableFiles: IndexableFiles) : FilesSetHandler<BitSet> {
  override fun createStateForProvider(): BitSet = BitSet()
  override fun addToState(state: BitSet, id: FileId) = state.set(id)
  override fun flushState(state: BitSet, iterator: IndexableFilesIterator) = indexableFiles.add(state, iterator)
}

private class IndexableFilesSetHandler(val indexableFiles: IndexableFiles) : FilesSetHandler<Unit> {
  override fun createStateForProvider() = Unit
  override fun addToState(state: Unit, id: FileId) = indexableFiles.add(id)
  override fun flushState(state: Unit, iterator: IndexableFilesIterator) = Unit
}

private sealed class HealthCheckErrorGroup(val fileIds: List<FileId>, val message: String) {
  val size: Int
    get() = fileIds.size

  fun fix(filter: ProjectIndexableFilesFilter) {
    for (fileId in fileIds) {
      fix(fileId, filter)
    }
  }

  suspend fun logMessage() {
    if (fileIds.isEmpty()) return

    val message = readAction {
      "${message}. Errors count: ${fileIds.size}. Examples:\n" + fileIds.joinToString("\n", limit = 5) { error ->
        fileInfo(error)
      }
    }
    log(message)
  }

  abstract fun fileInfo(fileId: FileId): String
  abstract fun fix(fileId: FileId, filter: ProjectIndexableFilesFilter)
  abstract fun log(message: String)
}

private class NonIndexableFilesInFilterGroup(files: List<FileId>) : HealthCheckErrorGroup(files, "Following files are NOT indexable but they were found in filter") {
  override fun fix(fileId: FileId, filter: ProjectIndexableFilesFilter) {
    filter.removeFileId(fileId)
  }

  override fun log(message: String) {
    LOG.info(message)
  }

  override fun fileInfo(fileId: FileId): String {
    return fileId.fileInfo()
  }
}

private val shouldLogProviders = SystemProperties.getBooleanProperty("project.indexable.files.filter.health.check.log.provider", false) // may consume too much memory

private class IndexableFilesNotInFilterGroup(files: List<FileId>, private val shouldBeIndexableFiles: IndexableFiles) : HealthCheckErrorGroup(files, "Following files are indexable but they were NOT found in filter") {
  override fun fix(fileId: FileId, filter: ProjectIndexableFilesFilter) {
    filter.ensureFileIdPresent(fileId) { true }
  }

  override fun log(message: String) {
    LOG.warn(message)
  }

  override fun fileInfo(fileId: FileId): String {
    return if (shouldLogProviders) "${fileId.fileInfo()} provider=${shouldBeIndexableFiles.getProvider(fileId)?.debugName}"
    else fileId.fileInfo()
  }
}

private class IndexableFiles {
  private val allFiles: BitSet = BitSet()
  private val perProvider: MutableList<Pair<IndexableFilesIterator, BitSet>> = mutableListOf()

  val size = allFiles.size()

  fun add(fileId: FileId) {
    allFiles.set(fileId)
  }

  fun add(fileSet: BitSet, provider: IndexableFilesIterator) {
    allFiles.or(fileSet)
    perProvider.add(Pair(provider, fileSet))
  }

  operator fun get(fileId: FileId): Boolean {
    return allFiles[fileId]
  }

  fun getProvider(fileId: FileId): IndexableFilesIterator? {
    return perProvider.find { it.second.get(fileId) }?.first
  }
}

sealed interface HealthCheckResult

private class HealthCheckFinished(val nonIndexableFilesInFilter: NonIndexableFilesInFilterGroup, val indexableFilesNotInFilter: IndexableFilesNotInFilterGroup) : HealthCheckResult
private class HealthCheckCancelled(val reason: FilterActionCancellationReason) : HealthCheckResult

internal interface Computation<T> {
  fun compute(checkCancelled: () -> Unit): T
}

private suspend fun <T> Computation<T>.execute(delayBetweenAttempts: Duration, maxAttempts: Int, valueIfUnsuccessful: T): T {
  (0 until maxAttempts).forEach { attempt ->
    try {
      return this.compute {
        ProgressManager.checkCanceled() // this one should cancel coroutine
      }
    }
    catch (_: ProcessCanceledException) {
    }
    delay(delayBetweenAttempts)
  }
  return valueIfUnsuccessful
}

private fun <T> Computation<T>.takeIfInSmartMode(project: Project): Computation<T> {
  val outerCompute = this::compute

  return object : Computation<T> {
    override fun compute(checkCancelled: () -> Unit): T {
      return outerCompute {
        checkCancelled()
        if (DumbService.getInstance(project).isDumb) {
          throw ProcessCanceledException()
        }
      }
    }
  }
}

private fun <T> Computation<T>.takeIfNoChange(trackers: List<ModificationTracker>): Computation<T> {
  val outerCompute = this::compute

  return object : Computation<T> {
    override fun compute(checkCancelled: () -> Unit): T {
      val tracker = trackers.drop(1).fold(trackers.firstOrNull()) { t1, t2 -> t1?.plus(t2) }
      val modCount = tracker?.modificationCount

      return outerCompute {
        checkCancelled()
        if (tracker?.modificationCount != modCount) {
          throw ProcessCanceledException()
        }
      }
    }
  }
}

private fun <T> Computation<T>.takeIfNoChange(filter: ProjectIndexableFilesFilter): Computation<T> {
  return filter.takeIfNoChangesHappened(this)
}

private fun <T> Computation<T>.takeIfScanningIsCompleted(project: Project): Computation<T> {
  val outerCompute = this::compute

  return object : Computation<T> {
    override fun compute(checkCancelled: () -> Unit): T {
      val service = project.getService(ProjectIndexingDependenciesService::class.java)
      return outerCompute {
        checkCancelled()
        if (!service.isScanningAndIndexingCompleted()) {
          throw ProcessCanceledException()
        }
      }
    }
  }
}