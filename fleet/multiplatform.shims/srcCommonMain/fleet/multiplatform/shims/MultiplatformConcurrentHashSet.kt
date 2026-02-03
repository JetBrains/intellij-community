// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.multiplatform.shims

import fleet.util.multiplatform.linkToActual

fun <T> MultiplatformConcurrentHashSet(): MultiplatformConcurrentHashSet<T> = linkToActual()

interface MultiplatformConcurrentHashSet<out T> : Iterable<T> {
  companion object {
    fun <T> empty(): MultiplatformConcurrentHashSet<T> {
      return EmptySet as MultiplatformConcurrentHashSet<T>
    }
  }

  val size: Int
  fun add(element: @UnsafeVariance T): Boolean
  fun addAll(elements: Collection<@UnsafeVariance T>): Boolean
  fun remove(element: @UnsafeVariance T): Boolean
  operator fun contains(element: @UnsafeVariance T): Boolean
  fun isEmpty(): Boolean
  fun clear()
}

private object EmptySet : MultiplatformConcurrentHashSet<Nothing> {
  override val size: Int get() = 0
  override fun add(element: Nothing): Boolean = throw UnsupportedOperationException()
  override fun addAll(elements: Collection<Nothing>): Boolean = throw UnsupportedOperationException()
  override fun remove(element: Nothing): Boolean = false
  override fun contains(element: Nothing): Boolean = false
  override fun isEmpty(): Boolean = true
  override fun clear() = throw UnsupportedOperationException()
  override fun iterator(): Iterator<Nothing> = EmptyIterator
  override fun equals(other: Any?): Boolean = other is MultiplatformConcurrentHashSet<*> && other.isEmpty()
  override fun hashCode(): Int = 0
}
