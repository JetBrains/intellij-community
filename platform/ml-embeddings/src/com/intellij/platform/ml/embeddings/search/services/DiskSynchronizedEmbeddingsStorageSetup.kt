// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.services

import com.intellij.platform.ml.embeddings.search.indices.DiskSynchronizedEmbeddingSearchIndex
import com.intellij.platform.ml.embeddings.search.indices.IndexableEntity
import com.intellij.platform.ml.embeddings.search.utils.LowMemoryNotificationManager
import com.intellij.platform.util.progress.durationStep
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicReference

class DiskSynchronizedEmbeddingsStorageSetup<T : IndexableEntity>(
  private val index: DiskSynchronizedEmbeddingSearchIndex,
  private val indexSetupJob: AtomicReference<Job>,
  private val scanResult: ScanResult<T>,
  private val shouldShrinkIndex: Boolean,
) {
  private var shouldSaveToDisk = false

  suspend fun run() = coroutineScope {
    indexSetupJob.getAndSet(launch {
      if (shouldShrinkIndex) {
        index.onIndexingStart()
      }
      scanResult.flow.collect { flow ->
        // wait until all files are traversed (but maybe not all files' content parsed)
        scanResult.filesScanFinished.receiveCatching()
        // filesCount should not change by this moment
        val duration = 1.0 / scanResult.filesCount.get()
        durationStep(duration) {
          flow.filter { it.id !in index }
            .collect {
              if (index.checkCanAddEntry()) {
                shouldSaveToDisk = true
                EmbeddingIndexingTask.Add(listOf(it.id), listOf(it.indexableRepresentation)).run(index)
              }
              else {
                LowMemoryNotificationManager.getInstance().showNotification()
              }
            }
        }
      }
    })?.cancel()
  }

  fun onFinish(cs: CoroutineScope) {
    indexSetupJob.set(null)
    if (shouldShrinkIndex) {
      index.onIndexingFinish()
    }
    if (shouldSaveToDisk) {
      cs.launch(Dispatchers.IO) {
        index.saveToDisk()
      }
    }
  }
}