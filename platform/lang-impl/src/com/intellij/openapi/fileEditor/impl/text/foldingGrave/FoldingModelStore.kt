// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text.foldingGrave

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ShutDownTracker
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.io.*
import kotlinx.coroutines.*
import java.io.IOException
import java.nio.file.Path

internal class FoldingModelStore(
  private val storeName: String,
  private val scope: CoroutineScope,
  private val dispatcher: CoroutineDispatcher,
  @Volatile private var persistentMap: CachingPersistentMap<Int, FoldingState>?,
  @Volatile private var flushingJob: Job?,
) {
  operator fun get(fileId: Int): FoldingState? {
    val persistentMap = persistentMap
    if (persistentMap == null) {
      return null
    }
    try {
      return persistentMap[fileId]
    } catch (e: IOException) {
      logger.info("cannot get markup for file $fileId", e)
    }
    try {
      persistentMap.remove(fileId)
      persistentMap.flush()
    } catch (e: IOException) {
      logger.warn("recreating folding persistent map $storeName", e)
      recreateCache()
    }
    return null
  }

  operator fun set(fileId: Int, state: FoldingState) {
    val persistentMap = persistentMap
    if (persistentMap == null) {
      return
    }
    scope.launch(dispatcher) {
      try {
        persistentMap[fileId] = state
      } catch (e: IOException) {
        logger.info("cannot store folding state $state for file $fileId", e)
      }
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as FoldingModelStore
    return storeName == other.storeName
  }

  override fun hashCode() = storeName.hashCode()

  override fun toString(): String = "FoldingModelStore($storeName, $persistentMap)"

  private fun close(isAppShutDown: Boolean) {
    val persistentMap = persistentMap
    if (persistentMap == null) {
      return
    }
    if (!isAppShutDown) {
      allStores.remove(this)
    }
    try {
      flushingJob?.cancel()
      flushingJob = null
      persistentMap.close()
    } catch (e: IOException) {
      logger.info("error on persistent map close", e)
    }
  }

  private fun recreateCache() {
    try {
      close(isAppShutDown=false)
    } catch (ignored: IOException) {}
    val path = storePath(storeName)
    IOUtil.deleteAllFilesStartingWith(path)
    persistentMap = createPersistentMap(path)
    flushingJob = startFlushingJob(persistentMap, scope, dispatcher)
  }

  companion object {
    private val logger = Logger.getInstance(FoldingModelStore::class.java)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    private val allStores: MutableSet<FoldingModelStore> = ConcurrentCollectionFactory.createConcurrentSet()

    init {
      ShutDownTracker.getInstance().registerCacheShutdownTask { allStores.forEach { it.close(isAppShutDown=true) } }
    }

    fun create(project: Project, scope: CoroutineScope): FoldingModelStore {
      val storeName = project.name.trimLongString() + "-" + project.locationHash.trimLongString()
      val persistentMap = createPersistentMap(storePath(storeName))
      val store = FoldingModelStore(
        storeName,
        scope,
        dispatcher,
        persistentMap,
        startFlushingJob(persistentMap, scope, dispatcher)
      )
      scope.awaitCancellationAndInvoke {
        withContext(dispatcher) {
          store.close(isAppShutDown=false)
        }
      }
      allStores.add(store)
      return store
    }

    private fun startFlushingJob(
      cache: CachingPersistentMap<Int, FoldingState>?,
      scope: CoroutineScope,
      dispatcher: CoroutineDispatcher,
    ): Job? {
      return if (cache != null) {
        scope.launch(dispatcher) {
          val timeToFlush: Long = 1_000
          delay(timeToFlush)
          while (isActive) {
            cache.flush()
            delay(timeToFlush)
          }
        }
      } else {
        null
      }
    }

    private fun createPersistentMap(path: Path): CachingPersistentMap<Int, FoldingState>? {
      val mapBuilder = PersistentMapBuilder.newBuilder(
        path,
        EnumeratorIntegerDescriptor.INSTANCE,
        FoldingState.Companion.FoldingStateExternalizer
      ).withVersion(FoldingState.SERDE_VERSION)
      var map: PersistentMapImpl<Int, FoldingState>? = null
      var exception: IOException? = null
      val retryAttempts = 5
      for (i in 1..retryAttempts) {
        try {
          map = PersistentMapImpl(mapBuilder)
          break
        } catch (e: VersionUpdatedException) {
          logger.info("folding persistent map ${e.message}, attempt $i")
          exception = e
          IOUtil.deleteAllFilesStartingWith(path)
        } catch (e: IOException) {
          logger.info("error while creating persistent map, attempt $i", e)
          exception = e
          IOUtil.deleteAllFilesStartingWith(path)
        }
      }
      if (map == null) {
        logger.error("cannot create persistent map", exception)
        return null
      }
      logger.debug { "persistent map created with folding files count " + map.size }
      return CachingPersistentMap(map, inMemoryCount=20)
    }

    private fun String.trimLongString(): String = StringUtil.shortenTextWithEllipsis(this, 50, 10, "")
      .replace(Regex("[^\\p{IsAlphabetic}\\d]"), "")
      .replace(" ", "")
      .replace(StringUtil.NON_BREAK_SPACE, "")

    private fun storePath(storeName: String) = PathManager.getSystemDir().resolve("persistent-folding").resolve(storeName)
  }
}
