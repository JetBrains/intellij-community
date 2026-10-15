// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.async

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
sealed interface ListChange<V>
@ApiStatus.Internal
data class AddedFirst<V>(val value: V) : ListChange<V>
@ApiStatus.Internal
data class AddedLast<V>(val value: V) : ListChange<V>
@ApiStatus.Internal
data class AddedAllLast<V>(val values: List<V>) : ListChange<V>
@ApiStatus.Internal
open class Deleted<V>(val isDeleted: (V) -> Boolean) : ListChange<V>
@ApiStatus.Internal
class AllDeleted<V> : Deleted<V>({ true })
@ApiStatus.Internal
data class Updated<V>(val updater: (V) -> V) : ListChange<V>

/**
 * Applies a [ListChange] to a (fully loaded, non-null) list, returning the mutated list.
 */
@ApiStatus.Internal
fun <V> List<V>.applyListChange(change: ListChange<V>): List<V> = when (change) {
  is AddedFirst -> listOf(change.value) + this
  is AddedLast -> this + change.value
  is AddedAllLast -> this + change.values
  is Deleted -> filterNot(change.isDeleted)
  is Updated -> map(change.updater)
}