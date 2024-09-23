// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.jvm.models

import com.fasterxml.jackson.core.JsonProcessingException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.platform.ml.embeddings.jvm.artifacts.KInferenceLocalArtifactsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

/**
 * Thread-safe wrapper around [LocalEmbeddingServiceLoader] that caches [LocalEmbeddingService]
 * so that when the available heap memory is low, the neural network model is unloaded.
 */
@OptIn(FlowPreview::class)
@Service(Service.Level.APP)
class LocalEmbeddingServiceProviderImpl(cs: CoroutineScope) : LocalEmbeddingServiceProvider {
  private val offloadRequest = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val indexingSessionCount = AtomicInteger(0)

  // Allow garbage collector to free memory if the available heap size is low
  private var localServiceRef: AtomicReference<LocalEmbeddingService?> = AtomicReference(null)
  private val mutex = Mutex()

  init {
    cs.launch {
      offloadRequest.debounce(OFFLOAD_TIMEOUT).collectLatest { if (indexingSessionCount.get() == 0) cleanup() }
    }
  }

  suspend fun getService(downloadArtifacts: Boolean = false, scheduleCleanup: Boolean = true): LocalEmbeddingService? {
    return mutex.withLock {
      var service = localServiceRef.get()
      if (service == null) {
        service = if (ApplicationManager.getApplication().isUnitTestMode) {
          LocalEmbeddingServiceLoader().load(CustomRootDataLoader(testDataPath))
        }
        else {
          val artifactsManager = KInferenceLocalArtifactsManager.getInstance()
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
        if (scheduleCleanup) scheduleCleanup()
        logger.debug("Loaded local embedding model")
      }
      service
    }
  }

  fun cleanup() {
    localServiceRef.set(null)
    logger.debug("Offloaded local embedding model")
  }

  /**
   * Specify that the model should not be offloaded from memory during the [action]
   */
  suspend fun <T> indexingSession(action: suspend LocalEmbeddingService.() -> T): T? {
    indexingSessionCount.incrementAndGet()
    val result = try {
      getService(scheduleCleanup = false)?.action()
    } finally {
      indexingSessionCount.decrementAndGet()
    }
    scheduleCleanup()
    return result
  }

  /**
   * Request the offloading of model from memory after [OFFLOAD_TIMEOUT].
   * If a newer request happens, it replaces the older one.
   */
  fun scheduleCleanup() {
    // Do not perform model offloading during the indexing session
    if (indexingSessionCount.get() == 0) {
      check(offloadRequest.tryEmit(Unit))
    }
  }

  fun getServiceBlocking(
    downloadArtifacts: Boolean = false,
    scheduleCleanup: Boolean = true
  ): LocalEmbeddingService? = runBlockingCancellable {
    getService(downloadArtifacts, scheduleCleanup)
  }

  companion object {
    private val logger = Logger.getInstance(LocalEmbeddingServiceProviderImpl::class.java)

    fun getInstance(): LocalEmbeddingServiceProviderImpl = service()

    val testDataPath: Path by lazy {
      testDataPathOverride ?: File(PathManager.getCommunityHomePath()).resolve("platform/ml-embeddings/tests/testResources").toPath()
    }

    @get:ApiStatus.Internal
    @set:ApiStatus.Internal
    var testDataPathOverride: Path? = null

    private val OFFLOAD_TIMEOUT = 10.seconds
  }

  override suspend fun getService(): LocalEmbeddingService? {
    return getService(true, true)
  }
}