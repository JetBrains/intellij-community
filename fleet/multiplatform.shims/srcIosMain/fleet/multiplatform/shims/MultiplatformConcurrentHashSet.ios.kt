// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.multiplatform.shims


import fleet.util.multiplatform.Actual
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock

@Actual
internal fun <K> MultiplatformConcurrentHashSetNative(): MultiplatformConcurrentHashSet<K> = object : MultiplatformConcurrentHashSet<K> { // TODO proper multiplatform concurrent hash set
  val set = mutableSetOf<K>()
  private val lock = ReentrantLock()

  override fun iterator(): MutableIterator<K> = lock.withLock {
    set.iterator()
  }

  override fun add(element: K): Boolean = lock.withLock {
    set.add(element)
  }

  override fun remove(element: K): Boolean = lock.withLock {
    set.remove(element)
  }

  override fun addAll(elements: Collection<K>): Boolean = lock.withLock {
    set.addAll(elements)
  }

  override fun clear() {
    lock.withLock {
      set.clear()
    }
  }

  override val size: Int
    get() = lock.withLock { set.size }

  override fun isEmpty(): Boolean = lock.withLock {
    set.isEmpty()
  }

  override fun contains(element: K): Boolean = lock.withLock {
    set.contains(element)
  }
}