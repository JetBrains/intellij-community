// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

abstract class RefreshQueue {
  fun createSession(async: Boolean, recursive: Boolean, finishRunnable: Runnable?): RefreshSession {
    return createSession(async, recursive, finishRunnable, ModalityState.defaultModalityState())
  }

  abstract fun createSession(async: Boolean, recursive: Boolean, finishRunnable: Runnable?, state: ModalityState): RefreshSession

  fun refresh(async: Boolean, recursive: Boolean, finishRunnable: Runnable?, vararg files: VirtualFile) {
    refresh(async, recursive, finishRunnable, ModalityState.defaultModalityState(), *files)
  }

  fun refresh(async: Boolean, recursive: Boolean, finishRunnable: Runnable?, files: Collection<out VirtualFile>) {
    refresh(async, recursive, finishRunnable, ModalityState.defaultModalityState(), files)
  }

  fun refresh(async: Boolean, recursive: Boolean, finishRunnable: Runnable?, state: ModalityState, vararg files: VirtualFile) {
    val session = createSession(async, recursive, finishRunnable, state)
    session.addAllFiles(*files)
    session.launch()
  }

  fun refresh(async: Boolean, recursive: Boolean, finishRunnable: Runnable?, state: ModalityState, files: Collection<out VirtualFile>) {
    val session = createSession(async, recursive, finishRunnable, state)
    session.addAllFiles(files)
    session.launch()
  }

  /**
   * Refreshes the local files at [paths].
   *
   * The desktop local file system loads the missing children of loaded directories in one refresh session,
   * see [LocalFileSystem.refreshNioFiles]. Another local file system gets one
   * [VirtualFileManager.refreshAndFindFileByNioPath] lookup per path. A path that does not exist is skipped.
   */
  fun refreshPaths(async: Boolean, recursive: Boolean, finishRunnable: Runnable?, paths: Collection<Path>) {
    val local = StandardFileSystems.local()
    if (local is LocalFileSystem) {
      local.refreshNioFiles(paths, async, recursive, finishRunnable)
      return
    }
    val fileManager = VirtualFileManager.getInstance()
    refresh(async, recursive, finishRunnable, paths.mapNotNull { fileManager.refreshAndFindFileByNioPath(it) })
  }

  /**
   * Runs VFS refresh for [files] and suspends until refresh is completed.
   *
   * The refresh process gets canceled when the context [kotlinx.coroutines.Job] gets canceled.
   *
   * VFS refresh depends on modality states (see [ModalityState]). By default, it runs with [ModalityState.nonModal].
   * To execute suspending VFS refresh in the context of a modal dialog, consider using [com.intellij.util.ui.launchOnShow]:
   * ```kotlin
   * mySwingComponent.launchOnShow("myScope") {
   *   // this code runs with the modality of `component`
   *   RefreshQueue.getInstance().refresh(true, listOf(file))
   * }
   * ```
   * Alternatively, VFS refresh launched inside a modal progress will use the modality of the progress:
   * ```
   * runWithModalProgressBlocking(project, "Modal Title") {
   *   // this code runs with the modality of the progress
   *   RefreshQueue.getInstance().refresh(true, listOf(file))
   * }
   * ```
   *
   * This refresh is executed with the help of [com.intellij.openapi.application.backgroundWriteAction]
   */
  @ApiStatus.Experimental
  abstract suspend fun refresh(recursive: Boolean, files: List<VirtualFile>)

  /**
   * Similar to suspending [refresh], but the event collection phase can run in parallel to other refreshes.
   *
   * Normally, it makes little sense to run event collection phases in parallel, as they involve heavy IO work which can overload the IDE.
   * Hence, we do not allow more than one event collection phase at a time.
   * However, sometimes we need to perform refresh urgently, and we cannot wait for other potentially long event collection phases.
   * In these cases, it is possible to use this method.
   *
   * This refresh still ends with write action in an exclusive section.
   */
  @ApiStatus.Internal
  abstract suspend fun refreshWithHighPriority(recursive: Boolean, files: List<VirtualFile>)

  /**
   * Processes [events] in background write action and suspends until the processing is completed.
   */
  @ApiStatus.Internal
  abstract suspend fun processEvents(events: List<VFileEvent>)

  @ApiStatus.Internal
  abstract fun processEvents(async: Boolean, events: List<VFileEvent>)

  companion object {
    @JvmStatic
    fun getInstance(): RefreshQueue = ApplicationManager.getApplication().getService(RefreshQueue::class.java)
  }
}
