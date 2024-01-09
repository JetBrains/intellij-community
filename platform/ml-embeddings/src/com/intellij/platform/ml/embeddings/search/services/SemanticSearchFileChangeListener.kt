// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.services

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.waitForSmartMode
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
@Service(Service.Level.PROJECT)
class SemanticSearchFileChangeListener(private val project: Project, cs: CoroutineScope) : AsyncFileListener {
  private val reindexRequest = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val reindexQueue = AtomicReference(ConcurrentCollectionFactory.createConcurrentSet<VirtualFile>())

  private var trackFiles = false
  private var trackClasses = false
  private var trackSymbols = false

  private val mutex = ReentrantLock()

  init {
    cs.launch {
      project.waitForSmartMode()
      reindexRequest.debounce(1000.milliseconds).collectLatest {
        processFiles(reindexQueue.getAndSet(ConcurrentCollectionFactory.createConcurrentSet()))
      }
    }
  }

  fun changeEntityTracking(storage: DiskSynchronizedEmbeddingsStorage<*>, shouldTrack: Boolean) {
    mutex.withLock {
      when (storage) {
        is FileEmbeddingsStorage -> trackFiles = shouldTrack
        is ClassEmbeddingsStorage -> trackClasses = shouldTrack
        is SymbolEmbeddingStorage -> trackSymbols = shouldTrack
        else -> throw UnsupportedOperationException()
      }
    }
  }

  @TestOnly
  fun clearEvents() = reindexQueue.get().clear()

  override fun prepareChange(events: MutableList<out VFileEvent>): AsyncFileListener.ChangeApplier? {
    if (mutex.withLock { !trackFiles && !trackClasses && !trackSymbols }) return null
    events.forEach { it.file?.let { file -> reindexQueue.get().add(file) } }
    return object : AsyncFileListener.ChangeApplier {
      override fun afterVfsChange() {
        check(reindexRequest.tryEmit(Unit))
      }
    }
  }

  private suspend fun processFiles(queue: Set<VirtualFile>) {
    if (mutex.withLock { trackFiles }) project.serviceAsync<FileEmbeddingsStorage>().generateEmbeddingsIfNecessary(queue)
    if (mutex.withLock { trackClasses }) project.serviceAsync<ClassEmbeddingsStorage>().generateEmbeddingsIfNecessary(queue)
    if (mutex.withLock { trackSymbols }) project.serviceAsync<SymbolEmbeddingStorage>().generateEmbeddingsIfNecessary(queue)
  }

  companion object {
    fun getInstance(project: Project): SemanticSearchFileChangeListener = project.service()
  }
}