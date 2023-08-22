// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.snapshot

import com.intellij.util.indexing.ValueContainer
import com.intellij.util.indexing.containers.IntIdsIterator
import com.intellij.util.indexing.impl.InvertedIndexValueIterator
import com.intellij.util.indexing.impl.ValueContainerImpl
import java.util.function.IntPredicate

object EmptyValueContainer: ValueContainer<Nothing>() {
  override fun getValueIterator(): ValueIterator<Nothing> = EmptyValueIterator

  override fun size(): Int = 0
}

private object EmptyValueIterator: InvertedIndexValueIterator<Nothing> {
  override fun next() = throw IllegalStateException()

  override fun getInputIdsIterator(): IntIdsIterator = ValueContainerImpl.EMPTY_ITERATOR

  override fun remove() = throw IllegalStateException()

  override fun getValueAssociationPredicate(): IntPredicate = IntPredicate { false }

  override fun hasNext() = false

  override fun getFileSetObject(): Any? = null
}