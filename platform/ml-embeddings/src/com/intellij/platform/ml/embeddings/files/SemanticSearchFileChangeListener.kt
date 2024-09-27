// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.files

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.platform.ml.embeddings.settings.EmbeddingIndexSettingsImpl
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
class SemanticSearchFileChangeListener(cs: CoroutineScope, private val index: suspend (Project, List<VirtualFile>) -> Unit) : AsyncFileListener {
  private val reindexRequest = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val reindexQueue = AtomicReference(ConcurrentCollectionFactory.createConcurrentSet<VirtualFile>())

  init {
    cs.launch {
      reindexRequest.debounce(1000.milliseconds).collectLatest {
        processFiles(reindexQueue.getAndSet(ConcurrentCollectionFactory.createConcurrentSet()))
      }
    }
  }

  @TestOnly
  fun clearEvents() = reindexQueue.get().clear()

  override fun prepareChange(events: MutableList<out VFileEvent>): AsyncFileListener.ChangeApplier? {
    if (!EmbeddingIndexSettingsImpl.getInstance().shouldIndexAnythingFileBased) return null
    events.forEach { it.file?.let { file -> reindexQueue.get().add(file) } }
    return object : AsyncFileListener.ChangeApplier {
      override fun afterVfsChange() {
        check(reindexRequest.tryEmit(Unit))
      }
    }
  }

  private suspend fun processFiles(queue: Set<VirtualFile>) {
    val fileBasedIndex = FileBasedIndex.getInstance() as FileBasedIndexImpl
    val projectToFiles = readAction {
      queue.flatMap { fileBasedIndex.getContainingProjects(it).map { project -> project to it } }
    }.groupBy({ it.first }, { it.second })
    for ((project, files) in projectToFiles) {
      index(project, files)
    }
    // When we have a project:
    // val files = IndexableFilesIndex.getInstance(project).run { queue.filter { shouldBeIndexed(it) } }
  }
}