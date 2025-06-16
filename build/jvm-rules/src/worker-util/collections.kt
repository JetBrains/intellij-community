// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.bazel.jvm.util

import androidx.collection.MutableScatterMap
import androidx.collection.ObjectList
import androidx.collection.ScatterMap
import androidx.collection.ScatterSet
import androidx.collection.emptyScatterMap
import it.unimi.dsi.fastutil.Hash
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenCustomHashSet
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentHashSetOf

val emptyStringArray: Array<String> = emptyArray<String>()

private object SlowEqualsAwareHashStrategy : Hash.Strategy<Any> {
  override fun hashCode(o: Any?): Int = o?.hashCode() ?: 0

  override fun equals(a: Any?, b: Any?): Boolean = (a?.hashCode() ?: 0) == (b?.hashCode() ?: 0) && a == b
}

fun <T : Any> slowEqualsAwareHashStrategy(): Hash.Strategy<T> {
  @Suppress("UNCHECKED_CAST")
  return SlowEqualsAwareHashStrategy as Hash.Strategy<T>
}

fun <T : Any> linkedSet(): ObjectLinkedOpenCustomHashSet<T> = ObjectLinkedOpenCustomHashSet(slowEqualsAwareHashStrategy())

fun <T : Any> hashSet(expectedSize: Int): ObjectOpenCustomHashSet<T> = ObjectOpenCustomHashSet(expectedSize, slowEqualsAwareHashStrategy())

fun <T : Any> emptyList(): List<T> = java.util.List.of()

fun <T : Any> emptySet(): Set<T> = java.util.Set.of()

fun <K : Any, V : Any> emptyMap(): Map<K, V> = java.util.Map.of()

fun <T> Set<T>.concat(collection: Collection<T>): Set<T> {
  if (collection.isEmpty()) {
    return this
  }
  return this + collection
}

fun <V : Any> PersistentSet<V>.removeAll(values: ScatterSet<V>): PersistentSet<V> {
  if (values.isEmpty()) {
    return this
  }
  return mutate { builder ->
    values.forEach { builder.remove(it) }
  }
}

fun <V : Any> ScatterSet<V>.toPersistentHashSet(): PersistentSet<V> {
  return persistentHashSetOf<V>().mutate { builder ->
    forEach { builder.add(it) }
  }
}

fun <T : Any> ObjectList<T>.toLinkedSet(): MutableSet<T> {
  val result = ObjectLinkedOpenCustomHashSet<T>(size, slowEqualsAwareHashStrategy())
  forEach {
    result.add(it)
  }
  return result
}

inline fun <T, K, V> Array<out T>.toScatterMap(transform: (T, MutableScatterMap<K, V>) -> Unit): ScatterMap<K, V> {
  val result = MutableScatterMap<K, V>(size)
  for (t in this) {
    transform(t, result)
  }
  return result
}

inline fun <T, K, V> List<T>.toScatterMap(transform: (T, MutableScatterMap<K, V>) -> Unit): ScatterMap<K, V> {
  val result = MutableScatterMap<K, V>(size)
  for (t in this) {
    transform(t, result)
  }
  return result
}

inline fun <T> ScatterSet<T>.filterToList(predicate: (T) -> Boolean): List<T> {
  val result = ArrayList<T>()
  forEach {
    if (predicate(it)) {
      result.add(it)
    }
  }
  return result.ifEmpty { emptyList() }
}

fun <K, V> ScatterMap<K, V>.orEmpty(): ScatterMap<K, V> = if (isEmpty()) emptyScatterMap() else this

//inline fun <T> Iterable<T>.filterToScatterSet(predicate: (T) -> Boolean): ScatterSet<T> {
//  val result = MutableScatterSet<T>()
//  for (element in this) {
//    if (predicate(element)) {
//      result.add(element)
//    }
//  }
//  return result
//}