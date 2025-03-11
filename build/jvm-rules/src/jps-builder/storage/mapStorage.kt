// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage", "SSBasedInspection")

package org.jetbrains.bazel.jvm.jps.storage

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.PersistentBTreeEnumerator
import com.intellij.util.io.StorageLockContext
import com.intellij.util.io.Unmappable
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap
import org.h2.mvstore.MVMap
import org.h2.mvstore.MVStore
import org.jetbrains.bazel.jvm.slowEqualsAwareHashStrategy
import org.jetbrains.jps.dependency.ExternalizableGraphElement
import org.jetbrains.jps.dependency.Externalizer
import org.jetbrains.jps.dependency.Maplet
import org.jetbrains.jps.dependency.MapletFactory
import org.jetbrains.jps.dependency.MultiMaplet
import org.jetbrains.jps.dependency.Usage
import org.jetbrains.jps.dependency.impl.MemoryMultiMaplet
import org.jetbrains.jps.dependency.impl.MvStoreContainerFactory
import org.jetbrains.jps.dependency.storage.StringEnumerator
import org.jetbrains.jps.incremental.RebuildRequestedException
import org.jetbrains.jps.incremental.storage.runAllCatching
import java.io.Closeable
import java.io.IOException
import java.lang.AutoCloseable
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.function.IntFunction
import java.util.function.ToIntFunction

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
    // disable auto-commit based on the size of unsaved data and save once in 1 minute
    .autoCommitBufferSize(0)
    .open()
  storeErrorHandler.log = { logger<BazelPersistentMapletFactory>().error("Store error (db=$dbFile)", it) }
  // versioning isn't required, otherwise the file size will be larger than needed
  store.setVersionsToKeep(0)
  return store
}

internal class BazelPersistentMapletFactory private constructor(
  stringEnumeratorImpl: PersistentBTreeEnumerator<String>,
  private val store: MVStore,
) : MapletFactory, Closeable, MvStoreContainerFactory {
  companion object {
    internal fun open(rootDir: Path, dbFile: Path, span: Span): BazelPersistentMapletFactory {
      val stringEnumerator = PersistentBTreeEnumerator(
        /* file = */ rootDir.resolve("strings"),
        /* dataDescriptor = */ EnumeratorStringDescriptor.INSTANCE,
        /* initialSize = */ 4096,
        /* lockContext = */ StorageLockContext()
      )

      val store = executeOrCloseStorage(stringEnumerator) {
        tryOpenMvStore(dbFile = dbFile, span = span)
      }

      executeOrCloseStorage(stringEnumerator) {
        executeOrCloseStorage(AutoCloseable(store::closeImmediately)) {
          return BazelPersistentMapletFactory(stringEnumerator, store)
        }
      }
    }
  }

  override fun getStringEnumerator(): StringEnumerator {
    return stringEnumerator
  }

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

  private val stringEnumerator = CachingStringEnumerator(stringEnumeratorImpl)

  override fun <K : Any, V : Any> openMap(mapName: String, mapBuilder: MVMap.Builder<K, Set<V>>): MultiMaplet<K, V> {
    val map: MVMap<K, Set<V>> = try {
      store.openMap(mapName, mapBuilder)
    }
    catch (e: Throwable) {
      throw RebuildRequestedException(RuntimeException("Cannot open map $mapName, map will be removed", e))
    }

    return MvStoreMultiMaplet(map)
  }

  override fun <K : Any, V : Any> openInMemoryMap(): MultiMaplet<K, V> = MemoryMultiMaplet(null)

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
    // on close mv map, we may write to stringEnumerator, so, first flush mv map
    runAllCatching(sequence {
      yield { store.close(500) }
      yield { stringEnumerator.close() }
    })
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
  private val enumerator: PersistentBTreeEnumerator<String>,
) : StringEnumerator, ToIntFunction<String>, IntFunction<String> {
  // synchronized - we access data mostly in a single-threaded manner (cache per target)
  private val idToStringCache = Int2ObjectOpenHashMap<String>()
  private val stringToIdCache = Object2IntOpenCustomHashMap<String>(slowEqualsAwareHashStrategy())

  override fun applyAsInt(value: String): Int {
    return enumerator.enumerate(value)
  }

  override fun apply(value: Int): String {
    return enumerator.valueOf(value) ?: invalidIdError(value)
  }

  @Synchronized
  override fun enumerate(string: String): Int {
    return stringToIdCache.computeIfAbsent(string, this)
  }

  @Synchronized
  override fun valueOf(id: Int): String {
    return idToStringCache.computeIfAbsent(id, this)
  }

  @Synchronized
  fun close() {
    enumerator.close()
    // help GC
    idToStringCache.clear()
    stringToIdCache.clear()
  }
}