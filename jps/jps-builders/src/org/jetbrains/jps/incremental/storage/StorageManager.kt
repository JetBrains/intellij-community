// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.concurrency.SynchronizedClearableLazy
import org.h2.mvstore.MVMap
import org.h2.mvstore.MVStore
import org.h2.mvstore.MVStoreTool
import org.h2.mvstore.type.DataType
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.measureTime

private val MV_STORE_CACHE_SIZE_IN_MB = System.getProperty("jps.new.storage.cache.size.mb", "64").toInt()

private val LOG = logger<StorageManager>()

@ApiStatus.Internal
class StorageManager(@JvmField val file: Path)  {
  constructor(file: Path, store: MVStore) : this(file) {
    storeValue.value = store
  }

  private val storeValue = SynchronizedClearableLazy {
    LOG.debug { "Opening storage $file" }
    createOrResetMvStore(file = file, readOnly = false, logger = { m, e, isWarn ->
      if (isWarn) {
        LOG.warn(m, e)
      }
      else {
        LOG.error(m, e)
      }
    })
  }

  fun open() {
    storeValue.value
  }

  fun <K : Any, V : Any> openMap(name: String, keyType: DataType<K>, valueType: DataType<V>): MVMap<K, V> {
    LOG.debug { "Open map $name" }

    val mapBuilder = MVMap.Builder<K, V>()
    mapBuilder.setKeyType(keyType)
    mapBuilder.setValueType(valueType)
    return openMap(name, mapBuilder)
  }

  fun <K : Any, V : Any> openMap(name: String, mapBuilder: MVMap.Builder<K, V>): MVMap<K, V> {
    return openOrResetMap(
      store = storeValue.value,
      name = name,
      mapBuilder = mapBuilder,
      logger = { m, e, isWarn -> LOG.warn(m, e) },
    )
  }

  /** Only if error occurred */
  fun forceClose() {
    storeValue.drop()?.let {
      if (LOG.isDebugEnabled) {
        LOG.debug("Force closing storage $file", Throwable())
      }
      it.closeImmediately()
    }
  }

  fun close() {
    val store = storeValue.drop() ?: return

    if (LOG.isDebugEnabled) {
      LOG.debug("Closing storage $file", Throwable())
    }

    val isCompactOnClose = System.getProperty("jps.new.storage.compact.on.close", "false").toBoolean()
    store.close()
    if (isCompactOnClose && Files.exists(file)) {
      val time = measureTime {
        MVStoreTool.compact(file.toString(), false)
      }
      LOG.info("Compacted storage in $time")
    }
  }

  fun commit() {
    storeValue.valueIfInitialized?.tryCommit()
  }

  fun clearCache() {
    // set again to force to clear the cache (in kb)
    storeValue.valueIfInitialized?.cacheSize = MV_STORE_CACHE_SIZE_IN_MB * 1024
  }

  fun clean() {
    val store = storeValue.valueIfInitialized
    if (store == null) {
      Files.deleteIfExists(file)
    }
    else {
      // we cannot recreate the store if reference to map is already acquired - so, remove all maps
      for (mapName in store.mapNames) {
        store.removeMap(mapName)
      }
    }
  }

  fun removeMaps(targetId: String, targetTypeId: String) {
    val store = storeValue.value
    for (mapName in store.mapNames) {
      if (mapName.startsWith(getMapName(targetId = targetId, targetTypeId = targetTypeId, suffix = ""))) {
        store.removeMap(mapName)
      }
    }
  }

  fun getMapName(targetId: String, targetTypeId: String, suffix: String): String = "$targetId|$targetTypeId|$suffix"
}

private fun <K : Any, V: Any> openOrResetMap(
  store: MVStore,
  name: String,
  mapBuilder: MVMap.Builder<K, V>,
  logger: StoreLogger,
): MVMap<K, V> {
  try {
    return store.openMap(name, mapBuilder)
  }
  catch (e: Throwable) {
    logger("Cannot open map $name, map will be removed", e, true)
    try {
      store.removeMap(name)
    }
    catch (e2: Throwable) {
      e.addSuppressed(e2)
    }
  }
  return store.openMap(name, mapBuilder)
}

private fun createOrResetMvStore(
  file: Path?,
  @Suppress("SameParameterValue") readOnly: Boolean = false,
  logger: StoreLogger,
  autoCommitDelay: Int = 60_000,
): MVStore {
  // If read-only and DB does not yet exist, create an in-memory DB
  if (file == null || (readOnly && Files.notExists(file))) {
    // in-memory
    return tryOpenMvStore(file = null, readOnly = readOnly, autoCommitDelay = autoCommitDelay, logger = logger)
  }

  val markerFile = getInvalidateMarkerFile(file)
  if (Files.exists(markerFile)) {
    Files.deleteIfExists(file)
    Files.deleteIfExists(markerFile)
  }

  file.parent?.let { Files.createDirectories(it) }
  try {
    return tryOpenMvStore(file = file, readOnly = readOnly, autoCommitDelay = autoCommitDelay, logger = logger)
  }
  catch (e: Throwable) {
    logger("Cannot open cache storage, will be recreated", e, true)
  }

  Files.deleteIfExists(file)
  return tryOpenMvStore(file = file, readOnly = readOnly, autoCommitDelay = autoCommitDelay, logger = logger)
}

private fun getInvalidateMarkerFile(file: Path): Path = file.resolveSibling("${file.fileName}.invalidated")

@ApiStatus.Internal
fun tryOpenMvStore(
  file: Path?,
  readOnly: Boolean,
  autoCommitDelay: Int,
  logger: StoreLogger,
): MVStore {
  val storeErrorHandler = StoreErrorHandler(file, logger)
  val store = MVStore.Builder()
    .fileName(file?.toAbsolutePath()?.toString())
    .backgroundExceptionHandler(storeErrorHandler)
    .cacheSize(MV_STORE_CACHE_SIZE_IN_MB)
    .also {
      if (readOnly) {
        it.readOnly()
      }
      if (autoCommitDelay == 0) {
        it.autoCommitDisabled()
      }

      it
    }
    // disable auto-commit based on the size of unsaved data and save once in 1 minute
    .autoCommitBufferSize(0)
    .open()
  storeErrorHandler.isStoreOpened = true
  // versioning isn't required, otherwise the file size will be larger than needed
  store.setVersionsToKeep(0)

  // We do not disable auto-commit as JPS doesn't use Kotlin coroutines, so it's okay to use a separate daemon thread.
  // Additionally, we ensure that the write operation will not slow down any tasks,
  // as the actual save will be done in a background thread.
  // Use a 16MB BUFFER for auto-commit instead of the default 1MB -
  // if writes are performed too often, do not save intermediate B-Tree pages to disk.
  if (autoCommitDelay != 0) {
    store.autoCommitDelay = autoCommitDelay
  }
  return store
}

typealias StoreLogger = (message: String, error: Throwable, isWarn: Boolean) -> Unit

private class StoreErrorHandler(
  private val dbFile: Path?,
  private val logger: StoreLogger,
) : Thread.UncaughtExceptionHandler {
  @JvmField
  var isStoreOpened: Boolean = false

  override fun uncaughtException(t: Thread, e: Throwable) {
    if (isStoreOpened) {
      logger("Store error (db=$dbFile)", e, false)
    }
    else {
      logger("Store will be recreated (db=$dbFile)", e, true)
    }
  }
}