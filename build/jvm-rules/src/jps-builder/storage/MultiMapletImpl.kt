// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage", "SSBasedInspection")

package org.jetbrains.bazel.jvm.jps.storage

import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.KeyDescriptor
import com.intellij.util.io.PersistentHashMapValueStorage
import com.intellij.util.io.PersistentMapBuilder
import com.intellij.util.io.PersistentMapImpl
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import org.jetbrains.jps.dependency.MultiMaplet
import java.io.DataInput
import java.io.DataInputStream
import java.io.DataOutput
import java.nio.file.Path
import kotlin.math.absoluteValue

private val phmCreationOptions = PersistentHashMapValueStorage.CreationTimeOptions(false, false, false, false)

internal class MultiMapletImpl<K : Any, V : Any>(
  mapFile: Path,
  keyDescriptor: KeyDescriptor<K>,
  private val valueExternalizer: GraphDataExternalizer<V>,
) : MultiMaplet<K, V> {
  private val map: PersistentMapImpl<K, Set<V>>

  init {
    val builder = PersistentMapBuilder.newBuilder(mapFile, keyDescriptor, object : DataExternalizer<Set<V>> {
      override fun save(out: DataOutput, data: Set<V>) {
        out.writeInt(data.size)
        val o = valueExternalizer.wrapOutput(out)
        for (value in data) {
          valueExternalizer.externalizer.save(o, value)
        }
      }

      override fun read(`in`: DataInput): Set<V> {
        val result = ObjectOpenHashSet<V>()
        val stream = `in` as DataInputStream
        while (stream.available() > 0) {
          var size = stream.readInt()
          val isRemoval = size < 0
          if (!isRemoval) {
            result.ensureCapacity(result.size + size.absoluteValue)
          }
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
    map = PersistentMapImpl(builder, phmCreationOptions)
  }

  override fun containsKey(key: K): Boolean {
    return map.containsKey(key)
  }

  override fun get(key: K): Collection<V> {
    return map.get(key) ?: return emptySet()
  }

  override fun put(key: K, values: Iterable<V>) {
    val data: Set<V> = when (values) {
      is Set<*> -> values as Set<V>
      is Collection<*> -> {
        if (values.isEmpty()) {
          emptySet()
        }
        else {
          ObjectOpenHashSet(values as Collection<V>)
        }
      }

      else -> ObjectOpenHashSet(values.iterator())
    }

    if (data.isEmpty()) {
      map.remove(key)
    }
    else {
      map.put(key, data)
    }
  }

  override fun remove(key: K?) {
    map.remove(key)
  }

  override fun appendValue(key: K, value: V) {
    map.appendData(key) { out ->
      out.writeInt(1)
      valueExternalizer.externalizer.save(valueExternalizer.wrapOutput(out), value)
    }
  }

  override fun appendValues(key: K, values: Iterable<V>) {
    val size = values.count()
    if (size == 0) {
      return
    }

    map.appendData(key) { out ->
      out.writeInt(size)
      val o = valueExternalizer.wrapOutput(out)
      for (v in values) {
        valueExternalizer.externalizer.save(o, v)
      }
    }
  }

  override fun removeValue(key: K, value: V) {
    map.appendData(key) { out ->
      out.writeInt(-1)
      valueExternalizer.externalizer.save(valueExternalizer.wrapOutput(out), value)
    }
  }

  override fun removeValues(key: K, values: Iterable<V>) {
    val size = values.count()
    if (size == 0) {
      return
    }

    map.appendData(key) { out ->
      out.writeInt(-size)
      val o = valueExternalizer.wrapOutput(out)
      for (v in values) {
        valueExternalizer.externalizer.save(o, v)
      }
    }
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