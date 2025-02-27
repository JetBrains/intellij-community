@file:Suppress("UnstableApiUsage")

package org.jetbrains.bazel.jvm.jps.storage

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.KeyDescriptor
import com.intellij.util.io.Unmappable
import org.jetbrains.bazel.jvm.hashSet
import org.jetbrains.jps.dependency.BaseMaplet
import org.jetbrains.jps.dependency.ExternalizableGraphElement
import org.jetbrains.jps.dependency.Externalizer
import org.jetbrains.jps.dependency.Maplet
import org.jetbrains.jps.dependency.MapletFactory
import org.jetbrains.jps.dependency.MultiMaplet
import org.jetbrains.jps.dependency.Usage
import org.jetbrains.jps.dependency.impl.CachingMaplet
import org.jetbrains.jps.dependency.impl.CachingMultiMaplet
import org.jetbrains.jps.dependency.impl.GraphDataInputImpl
import org.jetbrains.jps.dependency.impl.GraphDataOutputImpl
import org.jetbrains.jps.incremental.storage.runAllCatching
import java.io.Closeable
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException
import java.lang.AutoCloseable
import java.nio.file.Path
import kotlin.math.max
import kotlin.math.min

private const val BASE_CACHE_SIZE = 512

private val cacheSize = BASE_CACHE_SIZE * min(max(1, (Runtime.getRuntime().maxMemory() / 1073741824L).toInt()), 5)

internal class BazelPersistentMapletFactory private constructor(
  private val rootDir: Path,
  private val stringEnumerator: DurableStringEnumerator,
) : MapletFactory, Closeable {
  companion object {
    internal fun open(rootDir: Path): BazelPersistentMapletFactory {
      return BazelPersistentMapletFactory(rootDir, DurableStringEnumerator.open(rootDir.resolve("strings")))
    }
  }

  private val maps = ArrayList<BaseMaplet<*>>()

  private val usageInterner: LoadingCache<Usage, Usage> = Caffeine.newBuilder().maximumSize(cacheSize.toLong()).build { it }
  private val usageInternerFunction: (Usage) -> Usage = { usageInterner.get(it) }

  override fun <K : Any, V : Any> createSetMultiMaplet(
    storageName: String,
    keyExternalizer: Externalizer<K>,
    valueExternalizer: Externalizer<V>
  ): MultiMaplet<K, V> {
    val container = CachingMultiMaplet(
      DurablePersistentMultiMaplet(
        mapFile = rootDir.resolve(storageName),
        keyDescriptor = GraphKeyDescriptor(keyExternalizer, stringEnumerator),
        valueExternalizer = GraphDataExternalizer(valueExternalizer, stringEnumerator, usageInternerFunction),
        collectionFactory = { hashSet() },
        emptyCollection = emptySet(),
      ),
      cacheSize,
    )
    maps.add(container)
    return container
  }

  override fun <K : Any, V : Any> createMaplet(
    storageName: String,
    keyExternalizer: Externalizer<K>,
    valueExternalizer: Externalizer<V>,
  ): Maplet<K, V> {
    val container = CachingMaplet(
      //PersistentMaplet(
      //  getMapFile(storageName),
      //  GraphKeyDescriptor(keyExternalizer, stringEnumerator),
      //  GraphDataExternalizer(valueExternalizer, stringEnumerator, usageInternerFunction)
      //),
      DurableMapMaplet(
        rootDir.resolve(storageName),
        GraphKeyDescriptor(keyExternalizer, stringEnumerator),
        GraphDataExternalizer(valueExternalizer, stringEnumerator, usageInternerFunction),
      ),
      cacheSize,
    )
    maps.add(container)
    return container
  }

  override fun close() {
    runAllCatching(sequence {
      for (container in maps) {
        yield { container.close() }
      }
      yield { stringEnumerator.closeAndUnsafelyUnmap() }
    })
  }
}

private open class GraphDataExternalizer<T : Any>(
  private val externalizer: Externalizer<T>,
  private val stringEnumerator: DurableStringEnumerator,
  private val elementInterner: ((Usage) -> Usage)?,
) : DataExternalizer<T> {
  final override fun save(out: DataOutput, value: T?) {
    val wrapped = object : GraphDataOutputImpl(out) {
      override fun writeUTF(s: String) {
        writeInt(stringEnumerator.enumerate(s))
      }
    }
    externalizer.save(wrapped, value)
  }

  final override fun read(`in`: DataInput): T? {
    val wrapped = if (elementInterner == null) {
      object : GraphDataInputImpl(`in`) {
        override fun readUTF(): String {
          val id = readInt()
          return stringEnumerator.valueOf(id) ?: invalidIdError(id)
        }
      }
    }
    else {
      object : GraphDataInputImpl(`in`) {
        override fun readUTF(): String {
          val id = readInt()
          return stringEnumerator.valueOf(id) ?: invalidIdError(id)
        }

        override fun <T : ExternalizableGraphElement?> processLoadedGraphElement(element: T?): T? {
          @Suppress("UNCHECKED_CAST")
          return if (element is Usage) elementInterner(element) as T else element
        }
      }
    }
    return externalizer.load(wrapped)
  }
}

private fun invalidIdError(id: Int): Nothing {
  // throw IOException instead of IllegalStateException because in `PersistentEnumeratorBase.catchCorruption`
  // we wrap non-IOException into RuntimeException
  throw IOException("$id is not valid")
}

private class GraphKeyDescriptor<T : Any>(
  externalizer: Externalizer<T>,
  stringEnumerator: DurableStringEnumerator,
) : GraphDataExternalizer<T>(externalizer = externalizer, stringEnumerator = stringEnumerator, elementInterner = null), KeyDescriptor<T> {
  override fun isEqual(val1: T?, val2: T?): Boolean {
    return val1 == val2
  }

  override fun getHashCode(value: T?): Int {
    return value.hashCode()
  }
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