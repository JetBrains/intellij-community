@file:Suppress("UnstableApiUsage")

package org.jetbrains.bazel.jvm.jps.storage

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.KeyDescriptor
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
import org.jetbrains.jps.dependency.impl.PersistentMaplet
import org.jetbrains.jps.dependency.impl.PersistentMultiMaplet
import org.jetbrains.jps.incremental.storage.runAllCatching
import java.io.Closeable
import java.io.DataInput
import java.io.DataOutput
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier
import kotlin.math.max
import kotlin.math.min

private const val BASE_CACHE_SIZE = 512

private val cacheSize = BASE_CACHE_SIZE * min(max(1, (Runtime.getRuntime().maxMemory() / 1073741824L).toInt()), 5)

internal interface AsyncExecutor {
  fun <T> execute(action: () -> T): CompletableFuture<T>
}

internal class BazelPersistentMapletFactory(
  private val rootDir: Path,
  executor: AsyncExecutor,
) : MapletFactory, Closeable {
  private val maps = ArrayList<BaseMaplet<*>>()

  private val stringEnumeratorImpl = DurableStringEnumerator.openAsync(getMapFile("strings"), executor)

  private val stringEnumerator = object : StringEnumerator {
    override fun enumerate(string: String): Int {
      return stringEnumeratorImpl.enumerate(string)
    }

    override fun valueOf(id: Int): String? {
      return stringEnumeratorImpl.valueOf(id)
    }
  }

  private val usageInterner: LoadingCache<Usage, Usage> = Caffeine.newBuilder().maximumSize(cacheSize.toLong()).build { it }
  private val usageInternerFunction: (Usage) -> Usage = { usageInterner.get(it) }

  override fun <K, V> createSetMultiMaplet(
    storageName: String,
    keyExternalizer: Externalizer<K>,
    valueExternalizer: Externalizer<V>
  ): MultiMaplet<K, V> {
    val container = CachingMultiMaplet(
      PersistentMultiMaplet(
        getMapFile(storageName),
        GraphKeyDescriptor(keyExternalizer, stringEnumerator),
        GraphDataExternalizer(valueExternalizer, stringEnumerator, usageInternerFunction),
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
        GraphKeyDescriptor(keyExternalizer, stringEnumerator),
        GraphDataExternalizer(valueExternalizer, stringEnumerator, usageInternerFunction)
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
      yield { stringEnumeratorImpl.close() }
    })
  }

  private fun getMapFile(name: String): Path {
    val file = rootDir.resolve(name)
    Files.createDirectories(file.parent)
    return file
  }
}

private interface StringEnumerator {
  fun enumerate(string: String): Int

  fun valueOf(id: Int): String?
}

private open class GraphDataExternalizer<T>(
  private val externalizer: Externalizer<T>,
  private val stringEnumerator: StringEnumerator,
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
          return stringEnumerator.valueOf(id) ?: throw IllegalStateException("$id is not valid")
        }
      }
    }
    else {
      object : GraphDataInputImpl(`in`) {
        override fun readUTF(): String {
          val id = readInt()
          return stringEnumerator.valueOf(id) ?: throw IllegalStateException("$id is not valid")
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

private class GraphKeyDescriptor<T>(
  externalizer: Externalizer<T>,
  stringEnumerator: StringEnumerator,
) : GraphDataExternalizer<T>(externalizer = externalizer, stringEnumerator = stringEnumerator, elementInterner = null), KeyDescriptor<T> {
  override fun isEqual(val1: T?, val2: T?): Boolean {
    return val1 == val2
  }

  override fun getHashCode(value: T?): Int {
    return value.hashCode()
  }
}