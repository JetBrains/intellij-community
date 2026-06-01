// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.LinkedHashSet
import kotlin.time.Duration.Companion.milliseconds

private val scopedVfsRefreshLog = logger<ScopedVfsRefreshScheduler>()

// A single refresh processes at most this many roots; the overflow is carried to the next batch. Bounds the work of one refresh call and
// lets the gate and newer requests be re-evaluated between chunks during a large burst.
private const val DEFAULT_REFRESH_BATCH_LIMIT = 50

internal enum class ScopedVfsRefreshGate {
  Ready,
  RetryLater,
  DropPending,
}

/**
 * Coalesces scoped local VFS refresh requests.
 *
 * Duplicate paths are collapsed while they are still pending. Processing waits for the idle debounce once, then drains a batch, compacts it
 * to a minimal set of recursive roots, and refreshes it. Requests that arrive while a refresh is running are kept in a fresh pending set
 * and are processed as the next batch immediately after the current refresh finishes, without another debounce.
 *
 * Unprocessed paths are never lost: a drained path that is superseded by a newer pending request for the same or an ancestor path is
 * dropped from the current batch because the covering pending path will refresh it in the next batch. Temporary gates keep pending paths
 * for a later retry, while disabled sync drops them.
 *
 * A single refresh is capped at [refreshBatchLimit] roots; any overflow is carried to the next batch, where it is re-deduplicated against
 * newly scheduled requests.
 */
@OptIn(FlowPreview::class)
internal class ScopedVfsRefreshScheduler(private val refreshBatchLimit: Int = DEFAULT_REFRESH_BATCH_LIMIT) {
  init {
    require(refreshBatchLimit > 0) { "refreshBatchLimit must be positive" }
  }

  private val requests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private var pendingPaths = LinkedHashSet<Path>()
  private val pendingPathsLock = Any()

  fun schedule(paths: Collection<Path>) {
    if (paths.isEmpty()) {
      return
    }
    synchronized(pendingPathsLock) {
      pendingPaths.addAll(paths)
    }
    requestProcessing()
  }

  fun requestProcessing() {
    check(requests.tryEmit(Unit))
  }

  fun launchProcessing(
    coroutineScope: CoroutineScope,
    refreshQueue: RefreshQueue,
    refreshGate: () -> ScopedVfsRefreshGate,
    beforeRefresh: suspend () -> Unit,
  ) {
    coroutineScope.launch(CoroutineName("refresh scoped local roots requests flow processing")) {
      // not collectLatest - process pending paths sequentially and coalesce requests accumulated during a running refresh.
      requests
        .debounce(300.milliseconds)
        .collect {
          processPendingRefreshes(
            refreshQueue = refreshQueue,
            refreshGate = refreshGate,
            beforeRefresh = beforeRefresh,
          )
        }
    }
  }

  private fun drainPaths(): Set<Path> = synchronized(pendingPathsLock) {
    if (pendingPaths.isEmpty()) {
      emptySet()
    }
    else {
      val result = pendingPaths
      pendingPaths = LinkedHashSet<Path>()
      result
    }
  }

  private fun clearPendingPaths() {
    synchronized(pendingPathsLock) {
      pendingPaths = LinkedHashSet<Path>()
    }
  }

  private fun pendingPathsSnapshot(): Set<Path> = synchronized(pendingPathsLock) {
    if (pendingPaths.isEmpty()) emptySet() else LinkedHashSet(pendingPaths)
  }

  private fun requeuePaths(paths: List<Path>) {
    if (paths.isEmpty()) {
      return
    }
    synchronized(pendingPathsLock) {
      val newerPaths = pendingPaths
      pendingPaths = LinkedHashSet<Path>(paths.size + newerPaths.size)
      // carried-forward paths keep priority over requests scheduled while this batch was running
      pendingPaths.addAll(paths)
      pendingPaths.addAll(newerPaths)
    }
    requestProcessing()
  }

  private suspend fun processPendingRefreshes(
    refreshQueue: RefreshQueue,
    refreshGate: () -> ScopedVfsRefreshGate,
    beforeRefresh: suspend () -> Unit,
  ) {
    while (true) {
      when (refreshGate()) {
        ScopedVfsRefreshGate.Ready -> Unit
        ScopedVfsRefreshGate.RetryLater -> return
        ScopedVfsRefreshGate.DropPending -> {
          clearPendingPaths()
          return
        }
      }

      val paths = drainPaths()
      if (paths.isEmpty()) {
        return
      }

      // Drop paths superseded by newer pending requests, then collapse nested paths to a minimal set of recursive roots.
      val compacted = compactNestedPaths(dropPathsCoveredByNewerRequests(paths = paths, newerPaths = pendingPathsSnapshot()))
      if (compacted.isEmpty()) {
        continue
      }

      // Bound a single refresh: refresh at most refreshBatchLimit roots and carry the rest to the next batch.
      val refreshPaths = if (compacted.size <= refreshBatchLimit) {
        compacted
      }
      else {
        requeuePaths(compacted.subList(refreshBatchLimit, compacted.size))
        compacted.subList(0, refreshBatchLimit)
      }

      try {
        beforeRefresh()
        refreshScopedRoots(refreshQueue = refreshQueue, paths = refreshPaths)
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Exception) {
        // A failing batch must not tear down the scheduler (and with it the rest of SaveAndSyncHandler). Drop it and keep processing:
        // requeuing a deterministically failing path would create a busy-retry loop; the next schedule() retries naturally.
        scopedVfsRefreshLog.warn("Scoped VFS refresh failed", e)
      }
    }
  }

  private suspend fun refreshScopedRoots(refreshQueue: RefreshQueue, paths: Collection<Path>) {
    // resolveRefreshRoots does blocking VFS+IO (refreshAndFindFileByNioFile); keep it off the Default dispatcher.
    val roots = withContext(Dispatchers.IO) { resolveRefreshRoots(paths) }
    if (roots.isEmpty()) {
      return
    }

    scopedVfsRefreshLog.debug { "VFS refresh started (scoped roots=${roots.size})" }
    // RefreshQueue.refresh ties its session to the calling coroutine, so scope shutdown cancels the refresh.
    refreshQueue.refresh(recursive = true, files = roots)
  }
}

internal fun dropPathsCoveredByNewerRequests(paths: Collection<Path>, newerPaths: Collection<Path>): List<Path> {
  if (paths.isEmpty()) {
    return emptyList()
  }
  // Normalize once here; the returned paths are already normalized, so compactNestedPaths does not normalize them again.
  val normalizedPaths = paths.map { it.toAbsolutePath().normalize() }
  if (newerPaths.isEmpty()) {
    return normalizedPaths
  }

  val normalizedNewerPaths = newerPaths.mapTo(HashSet()) { it.toAbsolutePath().normalize() }
  return normalizedPaths.filterNot { path -> isCoveredBy(path, normalizedNewerPaths) }
}

private fun isCoveredBy(path: Path, coveringPaths: Set<Path>): Boolean {
  var current: Path? = path
  while (current != null) {
    if (current in coveringPaths) {
      return true
    }
    current = current.parent
  }
  return false
}

private fun resolveRefreshRoots(paths: Collection<Path>): List<NewVirtualFile> {
  val fileSystem = LocalFileSystem.getInstance()
  // paths are already deduplicated and compacted by processPendingRefreshes; only symlink/case collisions are resolved here.
  val roots = paths.mapNotNull { path ->
    fileSystem.refreshAndFindFileByNioFile(path) as? NewVirtualFile
  }
  return compactNestedVirtualFiles(roots)
}

private fun compactNestedPaths(paths: Collection<Path>): List<Path> {
  val result = ArrayList<Path>()
  val acceptedPaths = HashSet<Path>()
  // Paths are already normalized (see dropPathsCoveredByNewerRequests). Candidates are sorted shallow-first, so checking only the parent
  // chain is enough to find accepted ancestors.
  for (path in paths.asSequence().distinct().sortedBy { it.nameCount }) {
    if (!hasAcceptedAncestor(path, acceptedPaths)) {
      result.add(path)
      acceptedPaths.add(path)
    }
  }
  return result
}

private fun hasAcceptedAncestor(path: Path, acceptedPaths: Set<Path>): Boolean {
  var parent = path.parent
  while (parent != null) {
    if (parent in acceptedPaths) {
      return true
    }
    parent = parent.parent
  }
  return false
}

private fun compactNestedVirtualFiles(files: Collection<NewVirtualFile>): List<NewVirtualFile> {
  val result = ArrayList<NewVirtualFile>()
  val acceptedFiles = HashSet<NewVirtualFile>()
  // Avoid pairwise ancestor checks: every accepted ancestor is found by walking parents of the current candidate.
  for (file in files.sortedBy { it.path.length }) {
    if (file !in acceptedFiles && !hasAcceptedAncestor(file, acceptedFiles)) {
      result.add(file)
      acceptedFiles.add(file)
    }
  }
  return result
}

private fun hasAcceptedAncestor(file: NewVirtualFile, acceptedFiles: Set<NewVirtualFile>): Boolean {
  var parent = file.parent
  while (parent != null) {
    if (parent in acceptedFiles) {
      return true
    }
    parent = parent.parent
  }
  return false
}
