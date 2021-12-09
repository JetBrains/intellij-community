// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing

import com.intellij.ide.actions.SynchronizeCurrentFileAction
import com.intellij.ide.actions.cache.CacheInconsistencyProblem
import com.intellij.ide.actions.cache.RecoveryAction
import com.intellij.lang.LangBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
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

  override fun performSync(project: Project): List<CacheInconsistencyProblem> {
    //refresh files to be sure all changes processed before writing event log
    val localRoots = ManagingFS.getInstance().localRoots
    RefreshQueue.getInstance().refresh(false, true, null, *localRoots)

    val eventLog = EventLog()
    Disposer.newDisposable().use { actionDisposable ->
      project.messageBus.connect(actionDisposable).subscribe(VirtualFileManager.VFS_CHANGES, eventLog)

      val fileBasedIndex = FileBasedIndex.getInstance() as FileBasedIndexImpl
      val rootUrls = fileBasedIndex.getIndexableFilesProviders(project).flatMap { it.rootUrls }
      val files = arrayListOf<VirtualFile>()
      for (rootUrl in rootUrls) {
        val file = VirtualFileManager.getInstance().refreshAndFindFileByUrl(rootUrl)
        if (file != null) {
          files.add(file)
        }
      }
      SynchronizeCurrentFileAction.synchronizeFiles(files, project, false)
    }
    return eventLog.loggedEvents.map { it.toCacheInconsistencyProblem() }
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
        loggedEvents.add(Event(it.url))
      }
    }
  }

  private data class Event(val affectedFileUrl: String) {
    fun toCacheInconsistencyProblem(): CacheInconsistencyProblem {
      return object : CacheInconsistencyProblem {
        override val message: String
          get() = "vfs event on $affectedFileUrl"
      }
    }
  }
}