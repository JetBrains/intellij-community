// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import org.jetbrains.annotations.ApiStatus

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

  @ApiStatus.Internal
  abstract fun processEvents(async: Boolean, events: List<VFileEvent>)

  companion object {
    @JvmStatic
    fun getInstance(): RefreshQueue = ApplicationManager.getApplication().getService(RefreshQueue::class.java)
  }
}
