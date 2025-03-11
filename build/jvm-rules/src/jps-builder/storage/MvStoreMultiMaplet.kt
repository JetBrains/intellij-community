// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage", "SSBasedInspection", "ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.bazel.jvm.jps.storage

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.mutate
import org.h2.mvstore.MVMap
import org.jetbrains.jps.dependency.MultiMaplet
import kotlinx.collections.immutable.persistentHashSetOf
import org.jetbrains.jps.dependency.diff.Difference
import java.util.function.BiFunction
import kotlin.collections.addAll

internal class MvStoreMultiMaplet<K : Any, V : Any>(
  private val map: MVMap<K, Set<V>>,
) : MultiMaplet<K, V> {
  override fun containsKey(key: K): Boolean {
    return map.containsKey(key)
  }

  override fun get(key: K): Collection<V> {
    return map.get(key) ?: return emptySet()
  }

  override fun put(key: K, values: Iterable<V>) {
    val data: Set<V> = when (values) {
      is Set<*> -> values as Set<V>
      is Collection<*> -> {
        if (values.isEmpty()) {
          emptySet()
        }
        else {
          ObjectOpenHashSet(values as Collection<V>)
        }
      }
      else -> ObjectOpenHashSet(values.iterator())
    }

    if (data.isEmpty()) {
      map.remove(key)
    }
    else {
      map.put(key, data)
    }
  }

  override fun remove(key: K?) {
    map.remove(key)
  }

  override fun appendValue(key: K, value: V) {
    map.operate(key, null, AddToSetDecisionMaker(value))
  }

  private class AddToSetDecisionMaker<V>(private val toAdd: V) : MVMap.DecisionMaker<Set<V>>() {
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
      // we checked `contains` in `decide`
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

  override fun appendValues(key: K, values: Iterable<V>) {
    if (values.count() > 0) {
      map.operate(key, null, AddValuesToSetDecisionMaker(values))
    }
  }

  private class AddValuesToSetDecisionMaker<V>(private val toAdd: Iterable<V>) : MVMap.DecisionMaker<Set<V>>() {
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
      val m = if (existingValue.isNullOrEmpty()) {
        persistentHashSetOf()
      }
      else if (existingValue is PersistentSet<*>) {
        existingValue as PersistentSet<V>
      }
      else {
        return persistentHashSetOf<V>().mutate {
          it.addAll(existingValue as Collection<V>)
          it.addAll(toAdd)
        } as T
      }

      if (toAdd is Collection<*>) {
        return m.addAll(toAdd as Collection<V>) as T
      }
      else {
        return m.mutate { it.addAll(toAdd) } as T
      }
    }
  }

  override fun removeValue(key: K, value: V) {
    map.operate(key, null, RemoveValueDecisionMaker(value))
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

  override fun removeValues(key: K, values: Iterable<V>) {
    map.operate(key, null, RemoveValuesDecisionMaker(values))
  }

  private class RemoveValuesDecisionMaker<V>(private val toRemove: Iterable<V>) : MVMap.DecisionMaker<Set<V>>() {
    override fun decide(existingValue: Set<V>?, providedValue: Set<V>?): MVMap.Decision {
      if (existingValue.isNullOrEmpty() || toRemove.all { !existingValue.contains(it) }) {
        return MVMap.Decision.ABORT
      }
      else {
        return MVMap.Decision.PUT
      }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Set<V>> selectValue(existingValue: T?, providedValue: T?): T? {
      if (existingValue !is PersistentSet<*>) {
        return persistentHashSetOf<V>().mutate {
          it.addAll(existingValue as Collection<V>)
          for (v in toRemove) {
            it.remove(v)
          }
        } as T
      }

      val m = existingValue as PersistentSet<V>
      if (toRemove is Collection<*>) {
        return m.removeAll(toRemove as Collection<V>) as T
      }
      else {
        return m.mutate {
          for (v in toRemove) {
            it.remove(v)
          }
        } as T
      }
    }
  }


  override fun getKeys(): Iterable<K> {
    return map.keyList()
  }

  override fun close() {
  }

  override fun flush() {
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