// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.services

import com.fasterxml.jackson.core.JsonProcessingException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.platform.ml.embeddings.models.LocalEmbeddingService
import com.intellij.platform.ml.embeddings.models.LocalEmbeddingServiceLoader
import com.intellij.platform.util.coroutines.namedChildScope
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

/**
 * Thread-safe wrapper around [LocalEmbeddingServiceLoader] that caches [LocalEmbeddingService]
 * so that when the available heap memory is low, the neural network model is unloaded.
 */
@Service
class LocalEmbeddingServiceProvider(cs: CoroutineScope) {
  private val providerScope = cs.namedChildScope("Local embedding service provider scope")

  // Allow garbage collector to free memory if the available heap size is low
  private var localServiceRef: AtomicReference<LocalEmbeddingService?> = AtomicReference(null)
  private val mutex = Mutex()

  suspend fun getService(downloadArtifacts: Boolean = false): LocalEmbeddingService? {
    return mutex.withLock {
      var service = localServiceRef.get()
      if (service == null) {
        service = if (ApplicationManager.getApplication().isUnitTestMode) {
          LocalEmbeddingServiceLoader().load(CustomRootDataLoader(testDataPath))
        }
        else {
          val artifactsManager = LocalArtifactsManager.getInstance()
          if (!artifactsManager.checkArtifactsPresent()) {
            if (!downloadArtifacts) return null
            logger.debug("Downloading model artifacts because requested embedding calculation")
            artifactsManager.downloadArtifactsIfNecessary()
          }

          try {
            LocalEmbeddingServiceLoader().load(artifactsManager.getCustomRootDataLoader())
          }
          catch (e: NoSuchFileException) {
            logger.warn("Local embedding model artifacts not found: $e")
            null
          }
          catch (e: JsonProcessingException) {
            logger.warn("Local embedding model artifacts JSON processing failure")
            null
          }
        }
        localServiceRef.set(service)
      }
      service
    }
  }

  fun cleanup() {
    localServiceRef.set(null)
  }

  fun scheduleCleanup() {
    providerScope.coroutineContext.cancelChildren()
    providerScope.launch(providerScope.coroutineContext) {
      delay(serviceCleanupTimeout)
      cleanup()
    }
  }

  fun getServiceBlocking(downloadArtifacts: Boolean = false): LocalEmbeddingService? = runBlockingCancellable {
    getService(downloadArtifacts)
  }

  companion object {
    private val logger = Logger.getInstance(LocalEmbeddingServiceProvider::class.java)

    fun getInstance(): LocalEmbeddingServiceProvider = service()

    val testDataPath: Path by lazy {
      File(PathManager.getCommunityHomePath()).resolve("platform/ml-embeddings/tests/testResources").toPath()
    }

    val serviceCleanupTimeout = 10.seconds
  }
}