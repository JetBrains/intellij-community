// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.platform.ml.embeddings.models.LocalEmbeddingService
import com.intellij.platform.ml.embeddings.models.LocalEmbeddingServiceLoader
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.NoSuchFileException
import java.lang.ref.SoftReference

/**
 * Thread-safe wrapper around [LocalEmbeddingServiceLoader] that caches [LocalEmbeddingService]
 * so that when the available heap memory is low, the neural network model is unloaded.
 */
@Service
class LocalEmbeddingServiceProvider {
  // Allow garbage collector to free memory if the available heap size is low
  private var localServiceRef: SoftReference<LocalEmbeddingService>? = null
  private val mutex = Mutex()

  suspend fun getService(downloadArtifacts: Boolean = false): LocalEmbeddingService? {
    return mutex.withLock {
      var service = localServiceRef?.get()
      if (service == null) {
        val artifactsManager = LocalArtifactsManager.getInstance()
        if (!artifactsManager.checkArtifactsPresent()) {
          if (!downloadArtifacts) return null
          logger.debug("Downloading model artifacts because requested embedding calculation")
          if (!ApplicationManager.getApplication().isUnitTestMode) {
            artifactsManager.downloadArtifactsIfNecessary()
          }
        }

        service = try {
          LocalEmbeddingServiceLoader().load(artifactsManager.getCustomRootDataLoader())
        }
        catch (e: NoSuchFileException) {
          logger.warn("Local embedding model artifacts not found: $e")
          null
        }
        localServiceRef = SoftReference(service)
      }
      service
    }
  }

  fun getServiceBlocking(downloadArtifacts: Boolean = false): LocalEmbeddingService? = runBlockingCancellable {
    getService(downloadArtifacts)
  }

  companion object {
    private val logger = Logger.getInstance(LocalEmbeddingServiceProvider::class.java)

    fun getInstance(): LocalEmbeddingServiceProvider = service()
  }
}