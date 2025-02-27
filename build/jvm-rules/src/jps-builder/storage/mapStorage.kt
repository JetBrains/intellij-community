// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage", "SSBasedInspection")

package org.jetbrains.bazel.jvm.jps.storage

import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import com.intellij.util.io.PersistentBTreeEnumerator
import com.intellij.util.io.StorageLockContext
import com.intellij.util.io.Unmappable
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap
import org.jetbrains.bazel.jvm.slowEqualsAwareHashStrategy
import org.jetbrains.jps.dependency.BaseMaplet
import org.jetbrains.jps.dependency.ExternalizableGraphElement
import org.jetbrains.jps.dependency.Externalizer
import org.jetbrains.jps.dependency.GraphDataOutput
import org.jetbrains.jps.dependency.Maplet
import org.jetbrains.jps.dependency.MapletFactory
import org.jetbrains.jps.dependency.MultiMaplet
import org.jetbrains.jps.dependency.Usage
import org.jetbrains.jps.dependency.impl.GraphDataInputImpl
import org.jetbrains.jps.dependency.impl.GraphDataOutputImpl
import org.jetbrains.jps.incremental.storage.runAllCatching
import java.io.Closeable
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException
import java.lang.AutoCloseable
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.function.IntFunction
import java.util.function.ToIntFunction

internal class BazelPersistentMapletFactory private constructor(
  private val rootDir: Path,
  private val stringEnumeratorImpl: PersistentBTreeEnumerator<String>,
) : MapletFactory, Closeable {
  companion object {
    internal fun open(rootDir: Path): BazelPersistentMapletFactory {
      val stringEnumerator = PersistentBTreeEnumerator(
        /* file = */ rootDir.resolve("strings"),
        /* dataDescriptor = */ EnumeratorStringDescriptor.INSTANCE,
        /* initialSize = */ 4096,
        /* lockContext = */ StorageLockContext()
      )
      return BazelPersistentMapletFactory(rootDir, stringEnumerator)
    }
  }

  private val maps = ArrayList<BaseMaplet<*>>()

  private val usageInterner = ConcurrentHashMap<Usage, Usage>()

  private val usageInternerFunction: (Usage) -> Usage = { usage ->
    usageInterner.computeIfAbsent(usage) { it }
  }

  // synchronized - we access data mostly in a single-threaded manner (cache per target)
  private val stringEnumerator: StringEnumerator = object : StringEnumerator, ToIntFunction<String>, IntFunction<String> {
    private val idToStringMap = Int2ObjectOpenHashMap<String>()
    private val stringToIdMap = Object2IntOpenCustomHashMap<String>(slowEqualsAwareHashStrategy())

    override fun applyAsInt(value: String): Int {
      return stringEnumeratorImpl.enumerate(value)
    }

    override fun apply(value: Int): String {
      return stringEnumeratorImpl.valueOf(value) ?: invalidIdError(value)
    }

    @Synchronized
    override fun enumerate(string: String): Int {
      return stringToIdMap.computeIfAbsent(string, this)
    }

    @Synchronized
    override fun valueOf(id: Int): String {
      return idToStringMap.computeIfAbsent(id, this)
    }
  }

  override fun <K : Any, V : Any> createSetMultiMaplet(
    storageName: String,
    keyExternalizer: Externalizer<K>,
    valueExternalizer: Externalizer<V>
  ): MultiMaplet<K, V> {
    val container = CachingMultiMaplet(
      MultiMapletImpl(
        mapFile = rootDir.resolve(storageName),
        keyDescriptor = GraphKeyDescriptor(keyExternalizer, stringEnumerator),
        valueExternalizer = GraphDataExternalizer(
          externalizer = valueExternalizer,
          stringEnumerator = stringEnumerator,
          elementInterner = usageInternerFunction,
        ),
      ),
    )
    maps.add(container)
    return container
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
    runAllCatching(sequence {
      for (container in maps) {
        yield { container.close() }
      }
      yield { stringEnumeratorImpl.close() }
    })
  }
}

internal interface StringEnumerator {
  fun enumerate(string: String): Int

  fun valueOf(id: Int): String
}

internal open class GraphDataExternalizer<T : Any>(
  @JvmField val externalizer: Externalizer<T>,
  private val stringEnumerator: StringEnumerator,
  private val elementInterner: ((Usage) -> Usage)?,
) {
  fun wrapOutput(out: DataOutput): GraphDataOutput {
    return object : GraphDataOutputImpl(out) {
      override fun writeUTF(s: String) {
        writeInt(stringEnumerator.enumerate(s))
      }
    }
  }

  fun read(`in`: DataInput): T? {
    val wrapped = if (elementInterner == null) {
      object : GraphDataInputImpl(`in`) {
        override fun readUTF(): String {
          val id = readInt()
          return stringEnumerator.valueOf(id)
        }
      }
    }
    else {
      object : GraphDataInputImpl(`in`) {
        override fun readUTF(): String {
          val id = readInt()
          return stringEnumerator.valueOf(id)
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
  stringEnumerator: StringEnumerator,
) : GraphDataExternalizer<T>(externalizer = externalizer, stringEnumerator = stringEnumerator, elementInterner = null), KeyDescriptor<T> {
  override fun save(out: DataOutput, value: T) {
    externalizer.save(wrapOutput(out), value)
  }

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