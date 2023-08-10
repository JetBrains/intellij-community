// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.util

class CollectionDelta<out T>(oldCollection: Collection<T>, val newCollection: Collection<T>) {
  val newItems: Collection<T> = newCollection - oldCollection.asSet()
  val removedItems: Collection<T> = oldCollection - newCollection.asSet()

  private fun <T> Collection<T>.asSet() = ((this as? Set<T>) ?: toSet())

  val isEmpty = newItems.isEmpty() && removedItems.isEmpty()
}