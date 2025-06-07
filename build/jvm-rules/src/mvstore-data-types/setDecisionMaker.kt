// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.mvStore

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.collections.immutable.plus
import kotlinx.collections.immutable.toPersistentHashSet
import org.h2.mvstore.MVMap

class AddValueToSetDecisionMaker<V>(private val toAdd: V) : MVMap.DecisionMaker<Set<V>>() {
  override fun decide(existingValue: Set<V>?, providedValue: Set<V>?): MVMap.Decision {
    if (existingValue.isNullOrEmpty() || !existingValue.contains(toAdd)) {
      return MVMap.Decision.PUT
    }
    else {
      return MVMap.Decision.ABORT
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T : Set<V>> selectValue(existingValue: T?, providedValue: T?): T? {
    return when {
      existingValue.isNullOrEmpty() -> persistentHashSetOf(toAdd)
      existingValue is PersistentSet<*> -> (existingValue as PersistentSet<V>).add(toAdd)
      else -> {
        persistentHashSetOf<V>().mutate {
          it.addAll(existingValue as Collection<V>)
          it.add(toAdd)
        }
      }
    } as T
  }
}

class AddValuesToSetDecisionMaker<V>(private val toAdd: Iterable<V>) : MVMap.DecisionMaker<Set<V>>() {
  override fun decide(existingValue: Set<V>?, providedValue: Set<V>?): MVMap.Decision {
    if (existingValue.isNullOrEmpty() || toAdd.any { !existingValue.contains(it) }) {
      return MVMap.Decision.PUT
    }
    else {
      return MVMap.Decision.ABORT
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T : Set<V>> selectValue(existingValue: T?, providedValue: T?): T? {
    return when {
      existingValue.isNullOrEmpty() -> toAdd.toPersistentHashSet() as T
      existingValue is PersistentSet<*> -> (existingValue as PersistentSet<V>).plus(toAdd) as T
      else -> {
        return persistentHashSetOf<V>().mutate {
          it.addAll(existingValue as Collection<V>)
          it.addAll(toAdd)
        } as T
      }
    }
  }
}
