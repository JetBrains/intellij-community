@file:Suppress("UnstableApiUsage")

package org.jetbrains.bazel.jvm.jps.storage

import com.intellij.util.io.AppendablePersistentMap
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.KeyDescriptor
import com.intellij.util.io.PersistentMapBase
import com.intellij.util.io.PersistentMapBuilder
import org.jetbrains.jps.dependency.MultiMaplet
import java.io.DataInput
import java.io.DataInputStream
import java.io.DataOutput
import java.nio.file.Path
import kotlin.math.absoluteValue

internal class DurablePersistentMultiMaplet<K : Any, V : Any, C : MutableCollection<V>>(
  mapFile: Path,
  keyDescriptor: KeyDescriptor<K>,
  valueExternalizer: DataExternalizer<V>,
  private val collectionFactory: () -> C,
  private val emptyCollection: Collection<V>,
) : MultiMaplet<K, V> {
  private val map: PersistentMapBase<K, C>
  private val valueExternalizer: DataExternalizer<V>

  init {
    this.valueExternalizer = valueExternalizer

    val builder = PersistentMapBuilder.newBuilder<K, C>(mapFile, keyDescriptor, object : DataExternalizer<C> {
      override fun save(out: DataOutput, data: C) {
        out.writeInt(data.size)
        for (value in data) {
          valueExternalizer.save(out, value)
        }
      }

      override fun read(`in`: DataInput): C {
        val result = collectionFactory()
        val stream = `in` as DataInputStream
        while (stream.available() > 0) {
          var size = stream.readInt()
          val isRemoval = size < 0
          size = size.absoluteValue
          while (size-- > 0) {
            val v = valueExternalizer.read(stream)
            if (isRemoval) {
              result.remove(v)
            }
            else {
              result.add(v)
            }
          }
        }
        return result
      }
    })
    map = builder.buildImplementation()
  }

  override fun containsKey(key: K?): Boolean {
    return map.containsKey(key)
  }

  override fun get(key: K): Collection<V> {
    return map.get(key) ?: return emptyCollection
  }

  override fun put(key: K?, values: Iterable<V>) {
    val data = ensureCollection(values)
    if (data.isEmpty()) {
      map.remove(key)
    }
    else {
      map.put(key, data)
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun ensureCollection(seq: Iterable<V>): C {
    return when {
      emptyCollection is MutableSet<*> && seq is MutableSet<*> -> seq as C
      emptyCollection is MutableList<*> && seq is MutableList<*> -> seq as C
      else -> seq.toCollection(collectionFactory())
    }
  }

  override fun remove(key: K?) {
    map.remove(key)
  }

  override fun appendValue(key: K, value: V) {
    map.appendData(key, AppendablePersistentMap.ValueDataAppender { out ->
      out.writeInt(1)
      valueExternalizer.save(out, value)
    })
  }

  override fun appendValues(key: K, values: Iterable<V>) {
    val size = values.count()
    if (size == 0) {
      return
    }

    map.appendData(key, AppendablePersistentMap.ValueDataAppender { out ->
      out.writeInt(size)
      for (v in values) {
        valueExternalizer.save(out, v)
      }
    })
  }

  override fun removeValue(key: K, value: V) {
    removeValues(key, listOf(value))
  }

  override fun removeValues(key: K?, values: Iterable<V>) {
    val size = values.count()
    if (size == 0) {
      return
    }

    map.appendData(key, AppendablePersistentMap.ValueDataAppender { out ->
      out.writeInt(-size)
      for (v in values) {
        valueExternalizer.save(out, v)
      }
    })
  }

  override fun getKeys(): Iterable<K> {
    val result = ArrayList<K>(map.keysCount())
    map.processExistingKeys {
      result.add(it)
      true
    }
    return result
  }

  override fun close() {
    map.close()
  }

  override fun flush() {
    map.force()
  }
}