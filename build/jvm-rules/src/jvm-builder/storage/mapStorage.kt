// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage", "SSBasedInspection", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.worker.storage

import androidx.collection.MutableIntObjectMap
import androidx.collection.MutableObjectIntMap
import com.dynatrace.hash4j.hashing.HashValue128
import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.io.Unmappable
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.collections.immutable.PersistentSet
import org.h2.mvstore.MVMap
import org.h2.mvstore.MVStore
import org.jetbrains.bazel.jvm.mvStore.HashValue128KeyDataType
import org.jetbrains.bazel.jvm.mvStore.ModernStringDataType
import org.jetbrains.bazel.jvm.mvStore.MvStoreMapFactory
import org.jetbrains.bazel.jvm.mvStore.StringEnumerator
import org.jetbrains.bazel.jvm.mvStore.VarIntDataType
import org.jetbrains.bazel.jvm.worker.state.TargetConfigurationDigestContainer
import org.jetbrains.jps.dependency.ExternalizableGraphElement
import org.jetbrains.jps.dependency.Externalizer
import org.jetbrains.jps.dependency.Maplet
import org.jetbrains.jps.dependency.MapletFactory
import org.jetbrains.jps.dependency.MultiMaplet
import org.jetbrains.jps.dependency.Usage
import org.jetbrains.jps.dependency.impl.MemoryMultiMaplet
import org.jetbrains.jps.dependency.storage.MultiMapletEx
import org.jetbrains.jps.dependency.storage.MvStoreContainerFactory
import org.jetbrains.jps.dependency.storage.createImmutableIndexToStringMap
import org.jetbrains.jps.dependency.storage.createImmutableStringMap
import org.jetbrains.jps.incremental.RebuildRequestedException
import org.jetbrains.jps.incremental.storage.PathTypeAwareRelativizer
import org.jetbrains.jps.incremental.storage.RelativePathType
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.lang.AutoCloseable
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

private class StoreErrorHandler(
  @JvmField var log: (Throwable) -> Unit,
) : Thread.UncaughtExceptionHandler {
  override fun uncaughtException(t: Thread, e: Throwable) {
    log(e)
  }
}

private fun tryOpenMvStore(dbFile: Path, span: Span): MVStore {
  val storeErrorHandler = StoreErrorHandler(log = {
    span.recordException(it, Attributes.of(AttributeKey.stringKey("message"), dbFile.toString()))
  })
  val store = MVStore.Builder()
    .fileName(dbFile.toAbsolutePath().toString())
    .backgroundExceptionHandler(storeErrorHandler)
    // avoid extra thread - db maintainer should use coroutines
    .autoCommitDisabled()
    // default cache size is 16MB
    .cacheSize(128)
    // compilation is most single-threaded (we compute targets in parallel), reduce 16 to 8
    .cacheConcurrency(8)
    // disable auto-commit based on the size of unsaved data and save once in 1 minute
    .autoCommitBufferSize(0)
    .open()
  storeErrorHandler.log = { logger<BazelPersistentMapletFactory>().error("Store error (db=$dbFile)", it) }
  // versioning isn't required, otherwise the file size will be larger than needed
  store.setVersionsToKeep(0)
  return store
}

private val dictStringToInt = createImmutableStringMap()
private val dictIntToString = createImmutableIndexToStringMap()
private val stringEnumeratorOffset = dictStringToInt.size

private val stringHashToIndexMapBuilder = MVMap.Builder<HashValue128, Int>()
  .keyType(HashValue128KeyDataType)
  .valueType(VarIntDataType)

private val stringIndexToStringMapBuilder = MVMap.Builder<Int, String>()
  .keyType(VarIntDataType)
  .valueType(ModernStringDataType)

internal class BazelPersistentMapletFactory private constructor(
  private val store: MVStore,
  stringHashToIndexMap: MVMap<HashValue128, Int>,
  indexToStringMap: MVMap<Int, String>,
  private val pathRelativizer: PathTypeAwareRelativizer,
) : MapletFactory, Closeable, MvStoreContainerFactory {
  companion object {
    internal fun openStore(dbFile: Path, span: Span): MVStore {
      return tryOpenMvStore(dbFile = dbFile, span = span)
    }

    internal fun createFactory(store: MVStore, pathRelativizer: PathTypeAwareRelativizer): BazelPersistentMapletFactory {
      val storageCloser = AutoCloseable(store::closeImmediately)

      val stringHashToIndexMap = executeOrCloseStorage(storageCloser) {
        store.openMap("string-hash-to-index", stringHashToIndexMapBuilder)
      }
      val indexToStringMap = executeOrCloseStorage(storageCloser) {
        store.openMap("string-index-to-string", stringIndexToStringMapBuilder)
      }

      executeOrCloseStorage(storageCloser) {
        return BazelPersistentMapletFactory(store, stringHashToIndexMap, indexToStringMap, pathRelativizer)
      }
    }
  }

  @JvmField
  val mvstoreMapFactory: MvStoreMapFactory = object : MvStoreMapFactory {
    private val kotlinPathRelativizer = object : MvStoreMapFactory.LegacyKotlinPathRelativizer {
      override fun toRelative(file: File): String {
        return pathRelativizer.toRelative(file.path, RelativePathType.SOURCE)
      }

      override fun toAbsoluteFile(path: String): File {
        return File(pathRelativizer.toAbsolute(path, RelativePathType.SOURCE).replace('/', File.separatorChar))
      }
    }

    override fun <K : Any, V : Any> openMap(mapName: String, mapBuilder: MVMap.Builder<K, V>): MVMap<K, V> {
      return store.openMap(mapName, mapBuilder)
    }

    override fun getStringEnumerator(): StringEnumerator = stringEnumerator

    override fun getOldPathRelativizer(): MvStoreMapFactory.LegacyKotlinPathRelativizer = kotlinPathRelativizer
  }

  override fun getStringEnumerator(): StringEnumerator = stringEnumerator

  private val usageInterner = ConcurrentHashMap<Usage, Usage>()

  private val usageInternerFunction: (ExternalizableGraphElement) -> ExternalizableGraphElement = { element ->
    if (element is Usage) {
      usageInterner.computeIfAbsent(element) { it }
    }
    else {
      element
    }
  }

  override fun getElementInterner(): (ExternalizableGraphElement) -> ExternalizableGraphElement = usageInternerFunction

  private val stringEnumerator = CachingStringEnumerator(object : StringEnumerator {
    override fun enumerate(string: String): Int {
      return dictStringToInt.getOrElse(string) {
        val hash = Hashing.xxh3_128().hashBytesTo128Bits(string.toByteArray())
        stringHashToIndexMap.get(hash)?.let { return it }

        synchronized(indexToStringMap) {
          val newId = stringEnumeratorOffset + (indexToStringMap.lastKey() ?: -1) + 1
          val old = indexToStringMap.put(newId, string)
          require(old == null) { "Duplicate index $newId for string $string, old=$old" }
          stringHashToIndexMap.put(hash, newId)
          newId
        }
      }
    }

    @Synchronized
    override fun valueOf(id: Int): String {
      if (id < stringEnumeratorOffset) {
        return dictIntToString.get(id) ?: throw IOException("$id is not valid (stringEnumeratorOffset: $stringEnumeratorOffset)")
      }
      else {
        return indexToStringMap.get(id) ?: invalidIdError(id)
      }
    }
  })

  override fun <K : Any, V : Any> openMap(mapName: String, mapBuilder: MVMap.Builder<K, PersistentSet<V>>): MultiMapletEx<K, V> {
    val map: MVMap<K, PersistentSet<V>> = try {
      store.openMap(mapName, mapBuilder)
    }
    catch (e: Throwable) {
      throw RebuildRequestedException(RuntimeException("Cannot open map $mapName, map will be removed", e))
    }

    return MvStoreMultiMaplet(map)
  }

  override fun <K : Any, V : Any> openInMemoryMap(): MultiMapletEx<K, V> = MemoryMultiMaplet(null)

  override fun <K : Any, V : Any> createSetMultiMaplet(
    storageName: String,
    keyExternalizer: Externalizer<K>,
    valueExternalizer: Externalizer<V>
  ): MultiMaplet<K, V> {
    throw UnsupportedOperationException()
  }

  override fun <K : Any, V : Any> createMaplet(
    storageName: String,
    keyExternalizer: Externalizer<K>,
    valueExternalizer: Externalizer<V>,
  ): Maplet<K, V> {
    // actually, not used
    throw UnsupportedOperationException()
  }

  override fun close() {
    // during save, we enumerate strings, so we cannot save as a part of close,
    // as in this case string enumerator maps maybe not saved (as being closed)
    if (store.hasUnsavedChanges()) {
      store.commit()
      store.close()
    }
    else {
      // even if no changes, MvStore update file header -
      // we want to avoid unnecessary disk writes and file on disk with an updated "modified timestamp"
      store.closeImmediately()
    }
  }

  fun forceClose() {
    store.closeImmediately()
  }
}

private fun invalidIdError(id: Int): Nothing {
  // throw IOException instead of IllegalStateException because in `PersistentEnumeratorBase.catchCorruption`
  // we wrap non-IOException into RuntimeException
  throw IOException("$id is not valid")
}

internal inline fun <Out, In : AutoCloseable> executeOrCloseStorage(storageToClose: In, task: (In) -> Out): Out {
  try {
    return task(storageToClose)
  }
  catch (mainEx: Throwable) {
    try {
      if (storageToClose is Unmappable) {
        storageToClose.closeAndUnsafelyUnmap()
      }
      else {
        storageToClose.close()
      }
    }
    catch (closeEx: Throwable) {
      mainEx.addSuppressed(closeEx)
    }
    throw mainEx
  }
}

private class CachingStringEnumerator(
  private val enumerator: StringEnumerator,
) : StringEnumerator {
  // synchronized - we access data mostly in a single-threaded manner (cache per target)
  private val idToStringCache = MutableIntObjectMap<String>()
  private val stringToIdCache = MutableObjectIntMap<String>()

  @Synchronized
  override fun enumerate(string: String): Int {
    return stringToIdCache.getOrPut(string) { enumerator.enumerate(string) }
  }

  @Synchronized
  override fun valueOf(id: Int): String {
    return idToStringCache.getOrPut(id) { enumerator.valueOf(id) }
  }
}
