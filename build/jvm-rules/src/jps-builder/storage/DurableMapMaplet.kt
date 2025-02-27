@file:Suppress("UnstableApiUsage")

package org.jetbrains.bazel.jvm.jps.storage

import com.intellij.platform.util.io.storages.DataExternalizerEx
import com.intellij.platform.util.io.storages.KeyDescriptorEx
import com.intellij.platform.util.io.storages.durablemap.DurableMap
import com.intellij.platform.util.io.storages.durablemap.DurableMapFactory
import com.intellij.util.io.DataExternalizer
import com.intellij.util.io.KeyDescriptor
import org.jetbrains.jps.dependency.Maplet
import java.nio.file.Path

internal class DurableMapMaplet<K : Any, V : Any>(
  mapFile: Path,
  keyDescriptor: KeyDescriptor<K>,
  valueExternalizer: DataExternalizer<V>
) : Maplet<K, V> {
  private val map: DurableMap<K, V> = DurableMapFactory
    .withDefaults<K, V>(KeyDescriptorEx.adapt(keyDescriptor), DataExternalizerEx.adapt(valueExternalizer))
    .open(mapFile)

  override fun containsKey(key: K): Boolean {
    return map.containsMapping(key)
  }

  override fun get(key: K): V? {
    return map.get(key)
  }

  override fun put(key: K, value: V?) {
    map.put(key, value)
  }

  override fun remove(key: K) {
    map.remove(key)
  }

  override fun getKeys(): Iterable<K> {
    val result = ArrayList<K>(map.size())
    map.processKeys {
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