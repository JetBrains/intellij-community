// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.multiplatform.shims

internal object EmptyIterator : MutableListIterator<Nothing> {
  override fun hasNext(): Boolean = false
  override fun remove() = throw UnsupportedOperationException()
  override fun set(element: Nothing) = throw UnsupportedOperationException()
  override fun add(element: Nothing) = throw UnsupportedOperationException()
  override fun hasPrevious(): Boolean = false
  override fun nextIndex(): Int = 0
  override fun previousIndex(): Int = -1
  override fun next(): Nothing = throw NoSuchElementException()
  override fun previous(): Nothing = throw NoSuchElementException()
}