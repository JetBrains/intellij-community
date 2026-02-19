// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.multiplatform.shims

import fleet.util.multiplatform.Actual
import java.util.concurrent.ConcurrentHashMap

@Actual
internal fun <K> MultiplatformConcurrentHashSetJvm(): MultiplatformConcurrentHashSet<K> = MultiplatformConcurrentHashSetJvmImpl()

private class MultiplatformConcurrentHashSetJvmImpl<T> : MultiplatformConcurrentHashSet<T> {
  private val set = ConcurrentHashMap.newKeySet<T>()
  override val size: Int get() = set.size
  override fun add(element: T): Boolean = set.add(element)
  override fun iterator(): Iterator<T> = set.iterator()
  override fun addAll(elements: Collection<T>): Boolean = set.addAll(elements)
  override fun remove(element: T): Boolean = set.remove(element)
  override fun contains(element: T): Boolean = set.contains(element)
  override fun isEmpty(): Boolean = set.isEmpty()
  override fun clear() = set.clear()
}
