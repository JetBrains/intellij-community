// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.ide.actions.SynchronizeCurrentFileAction
import com.intellij.ide.actions.cache.CacheInconsistencyProblem
import com.intellij.ide.actions.cache.FilesRecoveryScope
import com.intellij.ide.actions.cache.RecoveryAction
import com.intellij.ide.actions.cache.RecoveryScope
import com.intellij.lang.LangBundle
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
class RefreshIndexableFilesAction : RecoveryAction {
  override val performanceRate: Int
    get() = 9999
  override val presentableName: @Nls(capitalization = Nls.Capitalization.Title) String
    get() = LangBundle.message("refresh.indexable.files.recovery.action.name")
  override val actionKey: String
    get() = "refresh"

  override fun performSync(recoveryScope: RecoveryScope): List<CacheInconsistencyProblem> {
    val project = recoveryScope.project
    //refresh files to be sure all changes processed before writing event log
    val rootsToRefresh = if (recoveryScope is FilesRecoveryScope) recoveryScope.files.toTypedArray() else ManagingFS.getInstance().localRoots
    RefreshQueue.getInstance().refresh(false, true, null, *rootsToRefresh)

    val eventLog = EventLog()
    Disposer.newDisposable().use { actionDisposable ->
      project.messageBus.connect(actionDisposable).subscribe(VirtualFileManager.VFS_CHANGES, eventLog)
      val files: Collection<VirtualFile>
      if (recoveryScope is FilesRecoveryScope) {
        files = recoveryScope.files
      } else {
        val fileBasedIndex = FileBasedIndex.getInstance() as FileBasedIndexImpl
        val rootUrls = fileBasedIndex.getIndexableFilesProviders(project).flatMap { it.getRootUrls(project) }
        files = rootUrls.mapNotNull { VirtualFileManager.getInstance().refreshAndFindFileByUrl(it) }
      }
      SynchronizeCurrentFileAction.synchronizeFiles(files, project, false)
    }
    return eventLog.loggedEvents
      .filter { event -> runReadAction { event.file.isValid && rootsToRefresh.any { it.isValid && VfsUtilCore.isAncestor(it, event.file, false) } } }
      .map { it.toCacheInconsistencyProblem() }
  }


  private class EventLog : BulkFileListener {
    val loggedEvents: MutableList<Event> = mutableListOf()

    override fun before(events: MutableList<out VFileEvent>) {
      for (event in events) {
        if (event is VFileCreateEvent) continue
        logEvent(event)
      }
    }

    override fun after(events: MutableList<out VFileEvent>) {
      for (event in events) {
        if (event is VFileCreateEvent) {
          logEvent(event)
        }
      }
    }

    private fun logEvent(event: VFileEvent) {
      event.file?.let {
        loggedEvents.add(Event(it))
      }
    }
  }

  private data class Event(val file: VirtualFile) {
    fun toCacheInconsistencyProblem(): CacheInconsistencyProblem {
      return object : CacheInconsistencyProblem {
        override val message: String
          get() = "vfs event on ${file.url}"
      }
    }
  }
}