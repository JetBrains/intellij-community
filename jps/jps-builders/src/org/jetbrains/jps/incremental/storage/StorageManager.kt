// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage

import com.intellij.openapi.diagnostic.Logger
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
class StorageManager(@JvmField val file: Path, private val allowedCompactionTimeOnClose: Int) {
  private val storeValue = SynchronizedClearableLazy {
    LOG.debug { "Opening storage $file" }
    createOrResetMvStore(file = file, readOnly = false, logSupplier = { LOG })
  }

  fun open() {
    storeValue.value
  }

  fun <K : Any, V : Any> openMap(name: String, keyType: DataType<K>, valueType: DataType<V>): MapHandle<K, V> {
    LOG.debug { "Open map $name" }

    val mapBuilder = MVMap.Builder<K, V>()
    mapBuilder.setKeyType(keyType)
    mapBuilder.setValueType(valueType)
    return openMap(name, mapBuilder)
  }

  fun <K : Any, V : Any> openMap(name: String, mapBuilder: MVMap.Builder<K, V>): MapHandle<K, V> {
    return MapHandle(openOrResetMap(store = storeValue.value, name = name, mapBuilder = mapBuilder, logSupplier = { LOG }))
  }

  /** Only if error occurred */
  fun forceClose() {
    if (LOG.isDebugEnabled) {
      LOG.debug("Force closing storage $file", Throwable())
    }

    storeValue.valueIfInitialized?.let {
      storeValue.drop()
      it.closeImmediately()
    }
  }

  fun close() {
    if (LOG.isDebugEnabled) {
      LOG.debug("Closing storage $file", Throwable())
    }

    storeValue.valueIfInitialized?.let {
      storeValue.drop()
      val isCompactOnClose = System.getProperty("jps.new.storage.compact.on.close", "true").toBoolean()
      it.close(if (isCompactOnClose) 0 else allowedCompactionTimeOnClose)
      if (isCompactOnClose && Files.exists(file)) {
        val time = measureTime {
          MVStoreTool.compact(file.toString(), false)
        }
        LOG.info("Compacted storage in $time")
      }
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

  fun removeMaps(targetId: String, typeId: String) {
    val store = storeValue.value
    for (mapName in store.mapNames) {
      if (mapName.startsWith(getMapName(targetId = targetId, targetTypeId = typeId, suffix = ""))) {
        store.removeMap(mapName)
      }
    }
  }

  fun getMapName(targetId: String, targetTypeId: String, suffix: String): String = "$targetId|$targetTypeId|$suffix"
}

@ApiStatus.Internal
class MapHandle<K : Any, V: Any> internal constructor(@JvmField val map: MVMap<K, V>)

private fun <K : Any, V: Any> openOrResetMap(
  store: MVStore,
  name: String,
  mapBuilder: MVMap.Builder<K, V>,
  logSupplier: () -> Logger,
): MVMap<K, V> {
  try {
    return store.openMap(name, mapBuilder)
  }
  catch (e: Throwable) {
    logSupplier().error("Cannot open map $name, map will be removed", e)
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
  logSupplier: () -> Logger,
): MVStore {
  // If read-only and DB does not yet exist, create an in-memory DB
  if (file == null || (readOnly && Files.notExists(file))) {
    // in-memory
    return tryOpenMvStore(file = null, readOnly = readOnly, logSupplier = logSupplier)
  }

  val markerFile = getInvalidateMarkerFile(file)
  if (Files.exists(markerFile)) {
    Files.deleteIfExists(file)
    Files.deleteIfExists(markerFile)
  }

  file.parent?.let { Files.createDirectories(it) }
  try {
    return tryOpenMvStore(file, readOnly, logSupplier)
  }
  catch (e: Throwable) {
    logSupplier().warn("Cannot open cache state storage, will be recreated", e)
  }

  Files.deleteIfExists(file)
  return tryOpenMvStore(file, readOnly, logSupplier)
}

private fun getInvalidateMarkerFile(file: Path): Path = file.resolveSibling("${file.fileName}.invalidated")

private fun tryOpenMvStore(file: Path?, readOnly: Boolean, logSupplier: () -> Logger): MVStore {
  val storeErrorHandler = StoreErrorHandler(file, logSupplier)
  val store = MVStore.Builder()
    .fileName(file?.toAbsolutePath()?.toString())
    .backgroundExceptionHandler(storeErrorHandler)
    // We do not disable auto-commit as JPS doesn't use Kotlin coroutines, so it's okay to use a separate daemon thread.
    // Additionally, we ensure that the write operation will not slow down any tasks,
    // as the actual save will be done in a background thread.
    // Use an 8MB threshold for auto-commit instead of the default 1MB -
    // if writes are performed too often, do not save intermediate B-Tree pages to disk.
    .autoCommitBufferSize(8192)
    .cacheSize(MV_STORE_CACHE_SIZE_IN_MB)
    .let {
      if (readOnly) it.readOnly() else it
    }
    .open()
  storeErrorHandler.isStoreOpened = true
  // versioning isn't required, otherwise the file size will be larger than needed
  store.setVersionsToKeep(0)
  return store
}

private class StoreErrorHandler(private val dbFile: Path?, private val logSupplier: () -> Logger) : Thread.UncaughtExceptionHandler {
  @JvmField
  var isStoreOpened: Boolean = false

  override fun uncaughtException(t: Thread, e: Throwable) {
    val log = logSupplier()
    if (isStoreOpened) {
      log.error("Store error (db=$dbFile)", e)
    }
    else {
      log.warn("Store will be recreated (db=$dbFile)", e)
    }
  }
}