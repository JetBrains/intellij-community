package org.jetbrains.bazel.jvm.jps.storage

import com.github.benmanes.caffeine.cache.Caffeine
import org.jetbrains.jps.dependency.MultiMaplet
import org.jetbrains.jps.dependency.diff.Difference
import java.util.function.BiFunction

internal class CachingMultiMaplet<K : Any, V : Any>(private val delegate: MultiMapletImpl<K, V>) : MultiMaplet<K, V> {
  private val cache = Caffeine.newBuilder().maximumSize(8_192).build(delegate::get)

  override fun containsKey(key: K): Boolean {
    return cache.getIfPresent(key) != null || delegate.containsKey(key)
  }

  override fun get(key: K): Iterable<V> {
    return cache.get(key)
  }

  override fun put(key: K, values: Iterable<V>) {
    try {
      delegate.put(key, values)
    }
    finally {
      cache.invalidate(key)
    }
  }

  override fun remove(key: K) {
    try {
      delegate.remove(key)
    }
    finally {
      cache.invalidate(key)
    }
  }

  override fun appendValue(key: K, value: V) {
    try {
      delegate.appendValue(key, value)
    }
    finally {
      cache.invalidate(key)
    }
  }

  override fun appendValues(key: K, values: Iterable<V>) {
    try {
      delegate.appendValues(key, values)
    }
    finally {
      cache.invalidate(key)
    }
  }

  override fun removeValue(key: K, value: V) {
    try {
      delegate.removeValue(key, value)
    }
    finally {
      cache.invalidate(key)
    }
  }

  override fun removeValues(key: K, values: Iterable<V>) {
    try {
      delegate.removeValues(key, values)
    }
    finally {
      cache.invalidate(key)
    }
  }

  override fun getKeys(): Iterable<K> {
    return delegate.getKeys()
  }

  override fun close() {
    delegate.close()
    // help GC
    cache.invalidateAll()
  }

  override fun flush() {
    delegate.flush()
  }

  override fun update(
    key: K,
    dataAfter: Iterable<V>,
    diffComparator: BiFunction<in Iterable<V>, in Iterable<V>, Difference.Specifier<out V, *>>,
  ) {
    val dataBefore = get(key)
    val beforeEmpty = dataBefore.none()
    val afterEmpty = dataAfter.none()
    if (beforeEmpty || afterEmpty) {
      if (!afterEmpty) {
        // so, before is empty
        appendValues(key, dataAfter)
      }
      else if (!beforeEmpty) {
        remove(key)
      }
    }
    else {
      val diff = diffComparator.apply(dataBefore, dataAfter)
      if (!diff.unchanged()) {
        if (diff.removed().none() && diff.changed().none()) {
          appendValues(key, diff.added())
        }
        else {
          put(key, dataAfter)
        }
      }
    }
  }
}