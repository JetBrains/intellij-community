// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.services

import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.isFile
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.platform.ml.embeddings.search.settings.SemanticSearchSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
@Service(Service.Level.PROJECT)
class SemanticSearchFileNameListener(private val project: Project, cs: CoroutineScope) : AsyncFileListener {
  private val reindexRequest = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val eventsQueue = ConcurrentLinkedQueue<VFileEvent>()

  init {
    cs.launch {
      reindexRequest.debounce(1000.milliseconds).collectLatest {
        val iterator = eventsQueue.iterator()
        while (iterator.hasNext()) {
          processEvent(iterator.next())
          iterator.remove()
        }
      }
    }
  }

  @TestOnly
  fun clearEvents() = eventsQueue.clear()

  override fun prepareChange(events: MutableList<out VFileEvent>): AsyncFileListener.ChangeApplier? {
    if (!SemanticSearchSettings.getInstance().enabledInFilesTab) return null
    events.forEach { eventsQueue.add(it) }
    return object : AsyncFileListener.ChangeApplier {
      override fun afterVfsChange() {
        check(reindexRequest.tryEmit(Unit))
      }
    }
  }

  private suspend fun processEvent(event: VFileEvent) {
    when (event) {
      is VFileCreateEvent -> {
        if (event.file?.isFile != true) return
        if (readAction { ProjectFileIndex.getInstance(project).isInSourceContent(event.file!!) }) {
          FileEmbeddingsStorage.getInstance(project).addEntity(IndexableFile(event.file!!))
        }
      }
      is VFileDeleteEvent -> {
        if (event.file.isDirectory) return
        val contentSourceRoots = ProjectRootManager.getInstance(project).contentSourceRoots
        // We can't use `isInSourceContent` here, because there is no file after delete operation
        if (contentSourceRoots.any { VfsUtilCore.getRelativePath(event.file, it) != null }) {
          FileEmbeddingsStorage.getInstance(project).deleteEntity(IndexableFile(event.file))
        }
      }
      is VFilePropertyChangeEvent -> {
        if (!event.file.isFile) return
        if (event.isRename && readAction { ProjectFileIndex.getInstance(project).isInSourceContent(event.file) }) {
          val oldName = event.oldValue as String
          FileEmbeddingsStorage.getInstance(project).renameFile(oldName, IndexableFile(event.file))
        }
      }
    }
  }

  companion object {
    fun getInstance(project: Project): SemanticSearchFileNameListener = project.service()
  }
}