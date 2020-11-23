// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs

import com.intellij.util.io.KeyDescriptor
import it.unimi.dsi.fastutil.Hash
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Predicate

@ApiStatus.Internal
object StubIndexKeyDescriptorCache {
  private val cache: MutableMap<StubIndexKey<*, *>, Pair<Hash.Strategy<*>, KeyDescriptor<*>>>
    = ConcurrentHashMap()

  @Suppress("UNCHECKED_CAST")
  fun <K> getKeyHashingStrategy(indexKey: StubIndexKey<K, *>) = getOrCache(indexKey).first as Hash.Strategy<K>

  @Suppress("UNCHECKED_CAST")
  fun <K> getKeyDescriptor(indexKey: StubIndexKey<K, *>): KeyDescriptor<K> {
    return getOrCache(indexKey).second as KeyDescriptor<K>
  }

  fun clear() {
    cache.clear()
  }

  private fun <K> getOrCache(indexKey: StubIndexKey<K, *>): Pair<Hash.Strategy<*>, KeyDescriptor<*>> {
    return cache.computeIfAbsent(indexKey) {
      val descriptor = indexKey.findExtension().keyDescriptor
      return@computeIfAbsent Pair(StubKeyHashingStrategy(descriptor), descriptor)
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun <K> StubIndexKey<K, *>.findExtension(): StubIndexExtension<K, *> {
    return StubIndexExtension.EP_NAME.findFirstSafe(Predicate { it.key == this }) as StubIndexExtension<K, *>
  }
}

private class StubKeyHashingStrategy<K>(private val descriptor: KeyDescriptor<K>): Hash.Strategy<K> {
  override fun equals(o1: K, o2: K) = o1 == o2 || (o1 != null && o2 != null && descriptor.isEqual(o1, o2))

  override fun hashCode(o: K) = if (o == null) 0 else descriptor.getHashCode(o)
}
