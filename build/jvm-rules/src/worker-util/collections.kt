// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.bazel.jvm

import it.unimi.dsi.fastutil.Hash
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenCustomHashSet
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet

private object SlowEqualsAwareHashStrategy : Hash.Strategy<Any> {
  override fun hashCode(o: Any?): Int = o?.hashCode() ?: 0

  override fun equals(a: Any?, b: Any?): Boolean = (a?.hashCode() ?: 0) == (b?.hashCode() ?: 0) && a == b
}

private fun <T> slowEqualsAwareHashStrategy(): Hash.Strategy<T> {
  @Suppress("UNCHECKED_CAST")
  return SlowEqualsAwareHashStrategy as Hash.Strategy<T>
}

fun <T : Any> linkedSet(): ObjectLinkedOpenCustomHashSet<T> = ObjectLinkedOpenCustomHashSet(slowEqualsAwareHashStrategy())

fun <T : Any> linkedSet(expectedSize: Int): ObjectLinkedOpenCustomHashSet<T> {
  return ObjectLinkedOpenCustomHashSet(expectedSize, slowEqualsAwareHashStrategy())
}

fun <T : Any> hashSet(): ObjectOpenCustomHashSet<T> = ObjectOpenCustomHashSet(slowEqualsAwareHashStrategy())

fun <T : Any> hashSet(expectedSize: Int): ObjectOpenCustomHashSet<T> = ObjectOpenCustomHashSet(expectedSize, slowEqualsAwareHashStrategy())

fun <K : Any, V : Any> hashMap(): Object2ObjectOpenCustomHashMap<K, V> {
  return Object2ObjectOpenCustomHashMap(slowEqualsAwareHashStrategy())
}

fun <K : Any, V : Any> hashMap(map: Map<K, V>): Object2ObjectOpenCustomHashMap<K, V> {
  return Object2ObjectOpenCustomHashMap(map, slowEqualsAwareHashStrategy())
}

fun <K : Any, V : Any> hashMap(size: Int): Object2ObjectOpenCustomHashMap<K, V> {
  return Object2ObjectOpenCustomHashMap(size, slowEqualsAwareHashStrategy())
}

fun <T : Any> emptyList(): List<T> = java.util.List.of()

fun <T : Any> emptySet(): Set<T> = java.util.Set.of()

fun <K : Any, V : Any> emptyMap(): Map<K, V> = java.util.Map.of()

fun <T> Set<T>.concat(collection: Collection<T>): Set<T> {
  if (collection.isEmpty()) {
    return this
  }
  return this + collection
}