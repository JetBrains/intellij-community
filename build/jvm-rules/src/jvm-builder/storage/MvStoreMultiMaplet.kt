// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage", "SSBasedInspection", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.worker.storage

import androidx.collection.ScatterSet
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.collections.immutable.plus
import kotlinx.collections.immutable.toPersistentHashSet
import org.h2.mvstore.MVMap
import org.jetbrains.bazel.jvm.mvStore.AddValueToSetDecisionMaker
import org.jetbrains.bazel.jvm.mvStore.AddValuesToSetDecisionMaker
import org.jetbrains.bazel.jvm.util.removeAll
import org.jetbrains.bazel.jvm.util.toPersistentHashSet
import org.jetbrains.jps.dependency.diff.Difference
import org.jetbrains.jps.dependency.storage.MultiMapletEx
import java.util.function.BiFunction

internal class MvStoreMultiMaplet<K : Any, V : Any>(
  private val map: MVMap<K, PersistentSet<V>>,
) : MultiMapletEx<K, V> {
  override fun containsKey(key: K): Boolean {
    return map.containsKey(key)
  }

  override fun get(key: K): Collection<V> {
    return map.get(key) ?: return persistentHashSetOf()
  }

  override fun put(key: K, values: ScatterSet<V>) {
    if (values.isEmpty()) {
      map.remove(key)
    }
    else {
      map.operate(key, values.toPersistentHashSet(), ReplaceValuesDecisionMaker)
    }
  }

  override fun put(key: K, values: Iterable<V>) {
    val newSet = values.toPersistentHashSet()
    if (newSet.isEmpty()) {
      map.remove(key)
    }
    else {
      map.operate(key, newSet, ReplaceValuesDecisionMaker)
    }
  }

  override fun remove(key: K) {
    map.remove(key)
  }

  override fun appendValue(key: K, value: V) {
    map.operate(key, null, AddValueToSetDecisionMaker(value))
  }

  override fun appendValues(key: K, values: Iterable<V>) {
    map.operate(key, null, AddValuesToSetDecisionMaker(values))
  }

  override fun removeValue(key: K, value: V) {
    map.operate(key, null, RemoveValueDecisionMaker(value))
  }

  override fun removeValues(key: K, values: Iterable<V>) {
    map.operate(key, null, RemoveValuesDecisionMaker(values))
  }

  override fun removeValues(key: K, values: ScatterSet<V>) {
    if (!values.isEmpty()) {
      map.operate(key, null, RemoveValuesAsScatterSetDecisionMaker(values))
    }
  }

  override fun getKeys(): Iterable<K> {
    return map.keys
  }

  override fun update(
    key: K,
    dataAfter: Iterable<V>,
    diffComparator: BiFunction<in Iterable<V>, in Iterable<V>, Difference.Specifier<out V, *>>,
  ) {
    throw UnsupportedOperationException("Use updateByDiff")
  }

  override fun close() {
  }

  override fun flush() {
  }
}

private class RemoveValueDecisionMaker<V>(private val toRemove: V) : MVMap.DecisionMaker<Set<V>>() {
  override fun decide(existingValue: Set<V>?, providedValue: Set<V>?): MVMap.Decision {
    return when {
      existingValue.isNullOrEmpty() || !existingValue.contains(toRemove) -> MVMap.Decision.ABORT
      // contains and size is 1 - remove set from the map
      existingValue.size == 1 -> MVMap.Decision.REMOVE
      else -> MVMap.Decision.PUT
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T : Set<V>> selectValue(existingValue: T?, providedValue: T?): T? {
    if (existingValue is PersistentSet<*>) {
      return (existingValue as PersistentSet<V>).remove(toRemove) as T
    }
    else {
      return persistentHashSetOf<V>().mutate {
        it.addAll(existingValue as Collection<V>)
        it.remove(toRemove)
      } as T
    }
  }
}

private class RemoveValuesDecisionMaker<V>(private val toRemove: Iterable<V>) : MVMap.DecisionMaker<PersistentSet<V>>() {
  override fun decide(existingValue: PersistentSet<V>?, providedValue: PersistentSet<V>?): MVMap.Decision {
    if (existingValue.isNullOrEmpty() || toRemove.all { !existingValue.contains(it) }) {
      return MVMap.Decision.ABORT
    }
    else {
      return MVMap.Decision.PUT
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T : PersistentSet<V>> selectValue(existingValue: T, providedValue: T?): T? {
    if (toRemove is Collection<*>) {
      return existingValue.removeAll(toRemove as Collection<V>) as T
    }
    else {
      return existingValue.mutate {
        for (v in toRemove) {
          it.remove(v)
        }
      } as T
    }
  }
}

private class RemoveValuesAsScatterSetDecisionMaker<V  : Any>(private val toRemove: ScatterSet<V>) : MVMap.DecisionMaker<PersistentSet<V>>() {
  override fun decide(existingValue: PersistentSet<V>?, providedValue: PersistentSet<V>?): MVMap.Decision {
    if (existingValue.isNullOrEmpty() || toRemove.all { !existingValue.contains(it) }) {
      return MVMap.Decision.ABORT
    }
    else {
      return MVMap.Decision.PUT
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T : PersistentSet<V>> selectValue(existingValue: T, providedValue: T?): T? {
    return existingValue.removeAll(toRemove) as T
  }
}

private object ReplaceValuesDecisionMaker : MVMap.DecisionMaker<Set<*>>() {
  override fun decide(existingValue: Set<*>?, providedValue: Set<*>?): MVMap.Decision {
    return if (existingValue == providedValue) MVMap.Decision.ABORT else MVMap.Decision.PUT
  }
}