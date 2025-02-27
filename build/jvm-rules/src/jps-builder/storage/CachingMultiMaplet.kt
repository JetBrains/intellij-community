package org.jetbrains.bazel.jvm.jps.storage

import org.jetbrains.jps.dependency.MultiMaplet
import org.jetbrains.jps.dependency.diff.Difference
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiFunction

// caffeine is not used as cache per target (so, a map is not big and closed quite often)
internal class CachingMultiMaplet<K : Any, V : Any>(private val delegate: MultiMapletImpl<K, V>) : MultiMaplet<K, V> {
  private val cache = ConcurrentHashMap<K, Iterable<V>>()

  override fun containsKey(key: K): Boolean {
    return cache.contains(key) || delegate.containsKey(key)
  }

  override fun get(key: K): Iterable<V> {
    return cache.computeIfAbsent(key, delegate::get)
  }

  override fun put(key: K, values: Iterable<V>) {
    try {
      delegate.put(key, values)
    }
    finally {
      cache.remove(key)
    }
  }

  override fun remove(key: K) {
    try {
      delegate.remove(key)
    }
    finally {
      cache.remove(key)
    }
  }

  override fun appendValue(key: K, value: V) {
    try {
      delegate.appendValue(key, value)
    }
    finally {
      cache.remove(key)
    }
  }

  override fun appendValues(key: K, values: Iterable<V>) {
    try {
      delegate.appendValues(key, values)
    }
    finally {
      cache.remove(key)
    }
  }

  override fun removeValue(key: K, value: V) {
    try {
      delegate.removeValue(key, value)
    }
    finally {
      cache.remove(key)
    }
  }

  override fun removeValues(key: K, values: Iterable<V>) {
    try {
      delegate.removeValues(key, values)
    }
    finally {
      cache.remove(key)
    }
  }

  override fun getKeys(): Iterable<K> {
    return delegate.getKeys()
  }

  override fun close() {
    delegate.close()
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