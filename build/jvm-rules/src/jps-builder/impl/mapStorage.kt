@file:Suppress("UnstableApiUsage")

package org.jetbrains.bazel.jvm.jps.impl

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.KeyDescriptor
import com.intellij.util.io.PersistentStringEnumerator
import com.intellij.util.io.StorageLockContext
import org.jetbrains.bazel.jvm.hashSet
import org.jetbrains.jps.dependency.BaseMaplet
import org.jetbrains.jps.dependency.Enumerator
import org.jetbrains.jps.dependency.Externalizer
import org.jetbrains.jps.dependency.Maplet
import org.jetbrains.jps.dependency.MapletFactory
import org.jetbrains.jps.dependency.MultiMaplet
import org.jetbrains.jps.dependency.Usage
import org.jetbrains.jps.dependency.impl.CachingMaplet
import org.jetbrains.jps.dependency.impl.CachingMultiMaplet
import org.jetbrains.jps.dependency.impl.GraphDataInputImpl
import org.jetbrains.jps.dependency.impl.GraphDataOutputImpl
import org.jetbrains.jps.dependency.impl.PersistentMaplet
import org.jetbrains.jps.dependency.impl.PersistentMultiMaplet
import org.jetbrains.jps.incremental.storage.runAllCatching
import java.io.Closeable
import java.io.DataInput
import java.io.DataOutput
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Function
import java.util.function.Supplier
import kotlin.math.max
import kotlin.math.min

private const val BASE_CACHE_SIZE = 512

private val cacheSize = BASE_CACHE_SIZE * min(max(1, (Runtime.getRuntime().maxMemory() / 1073741824L).toInt()), 5)

internal class BazelPersistentMapletFactory(
  private val rootDir: Path,
) : MapletFactory, Closeable {
  private val stringTable: PersistentStringEnumerator
  private val maps = ArrayList<BaseMaplet<*>>()
  private val enumerator: Enumerator
  private val usageInternerFunction: Function<Any?, Any?>
  private val usageInterner: LoadingCache<Usage, Usage>

  init {
    stringTable = PersistentStringEnumerator(getMapFile("string-table"), 4096, true, StorageLockContext())
    enumerator = object : Enumerator {
      override fun toString(num: Int): String? {
        return stringTable.valueOf(num)
      }

      override fun toNumber(str: String?): Int {
        return stringTable.enumerate(str)
      }
    }

    usageInterner = Caffeine.newBuilder().maximumSize(cacheSize.toLong()).build { it }
    usageInternerFunction = Function { if (it is Usage) usageInterner.get(it) else it }
  }

  override fun <K, V> createSetMultiMaplet(
    storageName: String,
    keyExternalizer: Externalizer<K>,
    valueExternalizer: Externalizer<V>
  ): MultiMaplet<K, V> {
    val container = CachingMultiMaplet(
      PersistentMultiMaplet(
        getMapFile(storageName),
        GraphKeyDescriptor(keyExternalizer, enumerator),
        GraphDataExternalizer(valueExternalizer, enumerator, usageInternerFunction),
        Supplier { hashSet() }
      ),
      cacheSize,
    )
    maps.add(container)
    return container
  }

  override fun <K, V> createMaplet(
    storageName: String,
    keyExternalizer: Externalizer<K?>,
    valueExternalizer: Externalizer<V?>,
  ): Maplet<K, V> {
    val container = CachingMaplet<K, V>(
      PersistentMaplet(
        getMapFile(storageName),
        GraphKeyDescriptor(keyExternalizer, enumerator),
        GraphDataExternalizer(valueExternalizer, enumerator, usageInternerFunction)
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
      yield { stringTable.close() }
    })
  }

  private fun getMapFile(name: String): Path {
    val file = rootDir.resolve(name)
    Files.createDirectories(file.parent)
    return file
  }
}

private open class GraphDataExternalizer<T>(
  private val externalizer: Externalizer<T>,
  private val enumerator: Enumerator?,
  private val objectInterner: Function<Any?, Any?>?,
) : DataExternalizer<T> {
  override fun save(out: DataOutput, value: T?) {
    externalizer.save(GraphDataOutputImpl.wrap(out, enumerator), value)
  }

  override fun read(`in`: DataInput): T? {
    return externalizer.load(GraphDataInputImpl.wrap(`in`, enumerator, objectInterner))
  }
}

private class GraphKeyDescriptor<T>(
  externalizer: Externalizer<T>,
  enumerator: Enumerator,
) : GraphDataExternalizer<T>(externalizer, enumerator, null), KeyDescriptor<T> {
  override fun isEqual(val1: T?, val2: T?): Boolean {
    return val1 == val2
  }

  override fun getHashCode(value: T?): Int {
    return value.hashCode()
  }
}