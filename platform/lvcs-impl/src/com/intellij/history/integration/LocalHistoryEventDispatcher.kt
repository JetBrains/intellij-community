// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.history.integration

import com.intellij.history.ActivityId
import com.intellij.history.core.LocalHistoryFacade
import com.intellij.history.integration.LocalHistoryImpl.Companion.getInstanceImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.command.CommandEvent
import com.intellij.openapi.command.CommandListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapi.vfs.VirtualFileManagerListener
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.util.containers.DisposableWrapperList

internal class LocalHistoryEventDispatcher(private val facade: LocalHistoryFacade, private val gateway: IdeaGateway) {
  private val vfsEventListeners = DisposableWrapperList<BulkFileListener>()

  fun startAction() {
    gateway.registerUnsavedDocuments(facade)
    facade.forceBeginChangeSet()
  }

  fun finishAction(name: @NlsContexts.Label String?, activityId: ActivityId?) {
    gateway.registerUnsavedDocuments(facade)
    endChangeSet(name, activityId)
  }

  private fun beginChangeSet() {
    facade.beginChangeSet()
  }

  private fun endChangeSet(name: @NlsContexts.Label String?, activityId: ActivityId?) {
    facade.endChangeSet(name, activityId)
  }

  private fun fileCreated(file: VirtualFile?) {
    if (file == null) return
    beginChangeSet()
    createRecursively(file)
    endChangeSet(null, null)
  }

  private fun createRecursively(dir: VirtualFile) {
    // Let's iterate only over content files.
    // It was already the case before this change. Iteration over VFS-cached files outside the project didn't do anything because
    // new non-indexable directories are not recursively loaded into VFS on creation.

    for (containingProjectIndex in IdeaGateway.getVersionedFilterData().myProjectFileIndices) {
      // It's unlikely that the same content directory belongs to multiple open projects, but for completeness let's iterate
      // over all of them as projects may have different excluded directories.
      containingProjectIndex.iterateContentUnderDirectory(dir, ContentIterator { fileOrDir -> // NOOP if dir is not under content roots
        if (isVersioned(fileOrDir)) {
          facade.created(gateway.getPathOrUrl(fileOrDir), fileOrDir.isDirectory)
        }
        true
      }, VirtualFileFilter { file -> isVersioned(file) })
    }

    val inContent = IdeaGateway.getVersionedFilterData().myProjectFileIndices.any { it.isInContent(dir) }

    if (!inContent && isVersioned(dir)) {
      // Non-recursively register this file or directory if it's outside project roots but is versioned.
      // (recursive iteration would load files into VFS, we don't want it)
      facade.created(gateway.getPathOrUrl(dir), dir.isDirectory)
    }
  }

  private fun beforeContentsChange(e: VFileContentChangeEvent) {
    val f = e.file
    if (!gateway.areContentChangesVersioned(f)) return

    val cachedDocument = FileDocumentManager.getInstance().getCachedDocument(f)
      ?.takeIf { it.modificationStamp == e.modificationStamp }

    val content = gateway.acquireActualContentAndForgetSavedContent(f, cachedDocument) ?: return
    //TODO RC: e.path already contains a path, compute it via f.getPath() is a waste of time
    facade.contentChanged(gateway.getPathOrUrl(f), content.content, content.timestamp)
  }

  private fun handleBeforeEvent(event: VFileEvent) {
    if (event is VFileContentChangeEvent) {
      beforeContentsChange(event)
    }
    else if (event is VFilePropertyChangeEvent && event.isRename || event is VFileMoveEvent) {
      val f = event.file!!
      f.putUserData(WAS_VERSIONED_KEY, gateway.isVersioned(f))
    }
    else if (event is VFileDeleteEvent) {
      beforeFileDeletion(event)
    }
  }

  private fun propertyChanged(e: VFilePropertyChangeEvent) {
    if (e.isRename) {
      val f = e.file

      val isVersioned = gateway.isVersioned(f)
      val wasVersioned = f.getUserData(WAS_VERSIONED_KEY) ?: return
      f.putUserData(WAS_VERSIONED_KEY, null)

      if (!wasVersioned && !isVersioned) return

      val oldName = e.oldValue as String
      facade.renamed(gateway.getPathOrUrl(f), oldName)
    }
    else if (VirtualFile.PROP_WRITABLE == e.propertyName) {
      if (!isVersioned(e.file)) return
      val f = e.file
      if (!f.isDirectory) {
        val oldWritableValue = e.oldValue as Boolean
        facade.readOnlyStatusChanged(gateway.getPathOrUrl(f), !oldWritableValue)
      }
    }
  }

  private fun fileMoved(e: VFileMoveEvent) {
    val f = e.file

    val isVersioned = gateway.isVersioned(f)
    val wasVersioned = f.getUserData(WAS_VERSIONED_KEY) ?: return
    f.putUserData(WAS_VERSIONED_KEY, null)

    if (!wasVersioned && !isVersioned) return

    facade.moved(gateway.getPathOrUrl(f), gateway.getPathOrUrl(e.oldParent))
  }

  private fun beforeFileDeletion(e: VFileDeleteEvent) {
    val f = e.file
    if (LocalHistoryFilesDeletionHandler.wasProcessed(f)) return
    val entry = gateway.createEntryForDeletion(f) ?: return
    facade.deleted(gateway.getPathOrUrl(f), entry)
  }

  private fun isVersioned(f: VirtualFile): Boolean = gateway.isVersioned(f)

  private fun handleBeforeEvents(events: List<VFileEvent>) {
    gateway.runWithVfsEventsDispatchContext(events, true) {
      for (event in events) {
        handleBeforeEvent(event)
      }
      for (listener in vfsEventListeners) {
        listener.before(events)
      }
    }
  }

  private fun handleAfterEvents(events: List<VFileEvent>) {
    gateway.runWithVfsEventsDispatchContext(events, false) {
      for (event in events) {
        handleAfterEvent(event)
      }
      for (listener in vfsEventListeners) {
        listener.after(events)
      }
    }
  }

  private fun handleAfterEvent(event: VFileEvent) {
    when (event) {
      is VFileCreateEvent -> fileCreated(event.getFile())
      is VFileCopyEvent -> fileCreated(event.findCreatedFile())
      is VFilePropertyChangeEvent -> propertyChanged(event)
      is VFileMoveEvent -> fileMoved(event)
    }
  }

  fun addVirtualFileListener(virtualFileListener: BulkFileListener, disposable: Disposable) {
    vfsEventListeners.add(virtualFileListener, disposable)
  }

  internal class LocalHistoryFileManagerListener : VirtualFileManagerListener {
    override fun beforeRefreshStart(asynchronous: Boolean) {
      getInstanceImpl().getEventDispatcher()?.beginChangeSet()
    }

    override fun afterRefreshFinish(asynchronous: Boolean) {
      getInstanceImpl().getEventDispatcher()?.endChangeSet(LocalHistoryBundle.message("activity.name.external.change"), CommonActivity.ExternalChange)
    }
  }

  internal class LocalHistoryCommandListener : CommandListener {
    override fun commandStarted(e: CommandEvent) {
      getInstanceImpl().getEventDispatcher()?.beginChangeSet()
    }

    override fun commandFinished(e: CommandEvent) {
      getInstanceImpl().getEventDispatcher()?.endChangeSet(e.commandName, CommonActivity.Command)
    }
  }

  internal class LocalHistoryBulkFileListener : BulkFileListener {
    override fun before(events: List<VFileEvent>) {
      getInstanceImpl().getEventDispatcher()?.handleBeforeEvents(events)
    }

    override fun after(events: List<VFileEvent>) {
      getInstanceImpl().getEventDispatcher()?.handleAfterEvents(events)
    }
  }

  companion object {
    private val WAS_VERSIONED_KEY = Key.create<Boolean>(LocalHistoryEventDispatcher::class.java.simpleName + ".WAS_VERSIONED_KEY")
  }
}