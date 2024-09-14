// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
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

@ApiStatus.Internal
class StorageManager(@JvmField val file: Path, private val allowedCompactionTimeOnClose: Int) {
  private val store = SynchronizedClearableLazy { createOrResetMvStore(file = file, readOnly = false, ::thisLogger) }

  fun open() {
    store.value
  }

  fun <K : Any, V : Any> openMap(name: String, keyType: DataType<K>, valueType: DataType<V>): MapHandle<K, V> {
    val mapBuilder = MVMap.Builder<K, V>()
    mapBuilder.setKeyType(keyType)
    mapBuilder.setValueType(valueType)
    return openMap(name, mapBuilder)
  }

  fun <K : Any, V : Any> openMap(name: String, mapBuilder: MVMap.Builder<K, V>): MapHandle<K, V> {
    return MapHandle(openOrResetMap(store = store.value, name = name, mapBuilder = mapBuilder, logSupplier = ::thisLogger))
  }

  /** Only if error occurred */
  fun forceClose() {
    store.valueIfInitialized?.closeImmediately()
  }

  fun close() {
    store.valueIfInitialized?.let {
      store.drop()
      val isCompactOnClose = System.getProperty("jps.new.storage.compact.on.close", "true").toBoolean()
      it.close(if (isCompactOnClose) 0 else allowedCompactionTimeOnClose)
      if (isCompactOnClose && Files.exists(file)) {
        val time = measureTime {
          MVStoreTool.compact(file.toString(), false)
        }
        thisLogger().info("Compacted storage in $time")
      }
    }
  }

  fun commit() {
    store.valueIfInitialized?.tryCommit()
  }

  fun clearCache() {
    // set again to force to clear the cache (in kb)
    store.valueIfInitialized?.cacheSize = MV_STORE_CACHE_SIZE_IN_MB * 1024
  }

  fun clean() {
    store.valueIfInitialized?.let {
      store.drop()
      it.closeImmediately()
      Files.deleteIfExists(file)
    }
  }

  fun removeStaleMaps(targetId: String, typeId: String) {
    val store = store.value
    for (mapName in store.mapNames) {
      if (mapName.startsWith(getMapName(targetId = targetId, typeId = typeId, suffix = ""))) {
        store.removeMap(mapName)
      }
    }
  }

  fun getMapName(targetId: String, typeId: String, suffix: String): String = "$targetId|$typeId|$suffix"
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
    // avoid extra thread - db maintainer should use coroutines
    .autoCommitDisabled()
    .autoCommitBufferSize(4096)
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