// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.services

import com.fasterxml.jackson.core.JsonProcessingException
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.platform.ml.embeddings.models.LocalEmbeddingService
import com.intellij.platform.ml.embeddings.models.LocalEmbeddingServiceLoader
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * Thread-safe wrapper around [LocalEmbeddingServiceLoader] that caches [LocalEmbeddingService]
 * so that when the available heap memory is low, the neural network model is unloaded.
 */
@Service
class LocalEmbeddingServiceProvider {
  // Allow garbage collector to free memory if the available heap size is low
  private val serviceCache: Cache<Unit, LocalEmbeddingService> =
    Caffeine.newBuilder().expireAfterAccess(30.seconds.toJavaDuration()).build()

  private val mutex = Mutex()

  suspend fun getService(downloadArtifacts: Boolean = false): LocalEmbeddingService? {
    return coroutineToIndicator { getServiceBlocking(downloadArtifacts) }
  }

  fun getServiceBlocking(downloadArtifacts: Boolean = false): LocalEmbeddingService? = serviceCache.get(Unit) {
    runBlockingCancellable {
      mutex.withLock {
        serviceCache.getIfPresent(Unit) ?: if (ApplicationManager.getApplication().isUnitTestMode) {
          LocalEmbeddingServiceLoader().load(CustomRootDataLoader(testDataPath))
        }
        else {
          val artifactsManager = LocalArtifactsManager.getInstance()
          if (!artifactsManager.checkArtifactsPresent()) {
            if (!downloadArtifacts) return@runBlockingCancellable null
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
      }
    }
  }

  companion object {
    private val logger = Logger.getInstance(LocalEmbeddingServiceProvider::class.java)

    fun getInstance(): LocalEmbeddingServiceProvider = service()

    val testDataPath: Path by lazy {
      File(PathManager.getCommunityHomePath()).resolve("platform/ml-embeddings/tests/testResources").toPath()
    }
  }
}