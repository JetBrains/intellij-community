// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs

import com.intellij.psi.PsiElement
import com.intellij.util.ObjectUtils
import com.intellij.util.containers.HashingStrategy
import com.intellij.util.indexing.impl.IndexStorageUtil
import com.intellij.util.io.KeyDescriptor
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Predicate

@ApiStatus.Internal
object StubIndexKeyDescriptorCache {
  private val cache: MutableMap<StubIndexKey<*, *>, Pair<HashingStrategy<*>, KeyDescriptor<*>>> = ConcurrentHashMap()
  private val charSeqExtensions: MutableMap<StubIndexKey<*, *>, Any?> = ConcurrentHashMap()

  @Suppress("UNCHECKED_CAST")
  fun <K> getKeyHashingStrategy(indexKey: StubIndexKey<K, *>) = getOrCache(indexKey).first as HashingStrategy<K>

  @Suppress("UNCHECKED_CAST")
  fun <K> getKeyDescriptor(indexKey: StubIndexKey<K, *>): KeyDescriptor<K> {
    return getOrCache(indexKey).second as KeyDescriptor<K>
  }

  fun clear() {
    cache.clear()
    charSeqExtensions.clear()
  }

  private fun <K> getOrCache(indexKey: StubIndexKey<K, *>): Pair<HashingStrategy<*>, KeyDescriptor<*>> {
    return cache.computeIfAbsent(indexKey) {
      val descriptor = indexKey.findExtension().keyDescriptor
      return@computeIfAbsent Pair(IndexStorageUtil.adaptKeyDescriptorToStrategy(descriptor), descriptor)
    }
  }

  @Suppress("UNCHECKED_CAST")
  fun <K, V : PsiElement> getKeyPsiMatcher(indexKey: StubIndexKey<K, V>, key: K): Predicate<V>? {
    if (key !is CharSequence) return null
    val extension = charSeqExtensions.computeIfAbsent(indexKey) {
      indexKey.findExtension() as? CharSequenceHashStubIndexExtension<V> ?: ObjectUtils.NULL
    } as? CharSequenceHashStubIndexExtension<V> ?: return null
    return Predicate { psi -> extension.doesKeyMatchPsi(key, psi) }
  }

  @Suppress("UNCHECKED_CAST")
  private fun <K> StubIndexKey<K, *>.findExtension(): StubIndexExtension<K, *> {
    val indexExtension = StubIndexExtension.EP_NAME.findFirstSafe(Predicate { it.key == this })
    if (indexExtension == null) {
      throw NullPointerException("Can't find stub index extension for key '$this'")
    }
    return indexExtension as StubIndexExtension<K, *>
  }
}
