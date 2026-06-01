// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.ide.GeneralSettings
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.NewVirtualFile
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.openapi.vfs.newvfs.RefreshSession
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.nio.file.Path
import java.util.LinkedHashSet
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

private val scopedVfsRefreshLog = logger<ScopedVfsRefreshScheduler>()

@OptIn(FlowPreview::class)
internal class ScopedVfsRefreshScheduler {
  private val requests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val pendingPaths = LinkedHashSet<Path>()
  private val pendingPathsLock = Any()

  fun schedule(paths: Collection<Path>) {
    synchronized(pendingPathsLock) {
      pendingPaths.addAll(paths)
    }
    check(requests.tryEmit(Unit))
  }

  fun launchProcessing(
    coroutineScope: CoroutineScope,
    settings: GeneralSettings,
    refreshQueue: RefreshQueue,
    isSyncBlocked: (GeneralSettings) -> Boolean,
    beforeRefresh: suspend () -> Unit,
  ) {
    coroutineScope.launch(CoroutineName("refresh scoped local roots requests flow processing")) {
      val refreshSession = AtomicReference<RefreshSession>()
      coroutineContext.job.invokeOnCompletion {
        refreshSession.getAndSet(null)?.cancel()
      }

      // not collectLatest - wait for previous execution
      requests
        .debounce(300.milliseconds)
        .collect {
          val paths = drainPaths()
          if (!isSyncBlocked(settings) && paths.isNotEmpty()) {
            beforeRefresh()
            refreshScopedRoots(refreshQueue, refreshSession, paths)
          }
        }
    }
  }

  private fun drainPaths(): List<Path> = synchronized(pendingPathsLock) {
    if (pendingPaths.isEmpty()) {
      emptyList()
    }
    else {
      pendingPaths.toList().also {
        pendingPaths.clear()
      }
    }
  }

  private fun refreshScopedRoots(refreshQueue: RefreshQueue, refreshSession: AtomicReference<RefreshSession>, paths: Collection<Path>) {
    val roots = resolveRefreshRoots(paths)
    if (roots.isEmpty()) {
      return
    }

    val session = refreshQueue.createSession(
      /* async = */ true,
      /* recursive = */ true,
      /* finishRunnable = */ null,
      /* state = */ ModalityState.nonModal(),
    )
    session.addAllFiles(roots)

    refreshSession.getAndSet(session)?.cancel()
    scopedVfsRefreshLog.debug { "VFS refresh started (scoped refreshRequests roots=${roots.size})" }
    session.launch()
  }
}

private fun resolveRefreshRoots(paths: Collection<Path>): List<NewVirtualFile> {
  val fileSystem = LocalFileSystem.getInstance()
  val roots = compactNestedPaths(paths).mapNotNull { path ->
    fileSystem.refreshAndFindFileByNioFile(path) as? NewVirtualFile
  }
  return compactNestedVirtualFiles(roots)
}

private fun compactNestedPaths(paths: Collection<Path>): List<Path> {
  val result = ArrayList<Path>()
  val acceptedPaths = HashSet<Path>()
  // Candidates are sorted shallow-first, so checking only the parent chain is enough to find accepted ancestors.
  for (path in paths.asSequence().map { it.toAbsolutePath().normalize() }.distinct().sortedBy { it.nameCount }) {
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
