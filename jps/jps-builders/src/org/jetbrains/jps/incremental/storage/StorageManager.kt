// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.storage

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import org.h2.mvstore.MVMap
import org.h2.mvstore.MVStore
import org.h2.mvstore.type.DataType
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path

internal const val MV_STORE_CACHE_SIZE_IN_MB = 32

@ApiStatus.Internal
class StorageManager(@JvmField val file: Path, allowedCompactionTimeOnClose: Int) {
  private val storeValue = StoreValue(file = file, allowedCompactionTimeOnClose = allowedCompactionTimeOnClose)

  fun <K : Any, V : Any> openMap(name: String, keyType: DataType<K>, valueType: DataType<V>): MapHandle<K, V> {
    val mapBuilder = MVMap.Builder<K, V>()
    mapBuilder.setKeyType(keyType)
    mapBuilder.setValueType(valueType)
    return openMap(name, mapBuilder)
  }

  fun <K : Any, V : Any> openMap(name: String, mapBuilder: MVMap.Builder<K, V>): MapHandle<K, V> {
    val store = storeValue.openStore()
    return MapHandle(storeValue, openOrResetMap(store = store, name = name, mapBuilder = mapBuilder, logSupplier = ::thisLogger))
  }

  /** Only if error occurred and you release all [MapHandle]s */
  fun forceClose() {
    storeValue.forceClose()
  }
}

internal class StoreValue(private val file: Path, private val allowedCompactionTimeOnClose: Int) {
  private var refCount = 0
  private var store: MVStore? = null

  @Synchronized
  fun forceClose() {
    refCount = 0
    store?.let {
      store = null
      it.closeImmediately()
    }
  }

  @Synchronized
  fun openStore(): MVStore {
    if (refCount == 0) {
      require(store == null)
      val store = createOrResetMvStore(file = file, readOnly = false, ::thisLogger)
      this.store = store
      refCount++
      return store
    }

    refCount++
    return store!!
  }

  @Synchronized
  fun release() {
    when (refCount) {
      1 -> {
        store!!.close(allowedCompactionTimeOnClose)
        store = null
        refCount = 0
      }
      0 -> throw IllegalStateException("Store is already closed")
      else -> refCount--
    }
  }
}

@ApiStatus.Internal
class MapHandle<K : Any, V: Any> internal constructor(
  private val storeValue: StoreValue,
  @JvmField val map: MVMap<K, V>,
) {
  @Volatile
  private var isReleased = false

  fun release() {
    if (!isReleased) {
      storeValue.release()
      isReleased = true
    }
  }

  fun tryCommit() {
    require(!isReleased)
    map.store.tryCommit()
  }
}

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