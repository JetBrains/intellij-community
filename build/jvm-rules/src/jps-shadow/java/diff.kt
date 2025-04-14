// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SSBasedInspection", "ReplaceGetOrSet")

package org.jetbrains.jps.dependency.java

import androidx.collection.ScatterSet
import it.unimi.dsi.fastutil.Hash.Strategy
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import kotlinx.collections.immutable.PersistentSet
import org.jetbrains.bazel.jvm.util.filterToList
import org.jetbrains.bazel.jvm.util.emptySet
import org.jetbrains.jps.dependency.diff.DiffCapable
import org.jetbrains.jps.dependency.diff.Difference
import org.jetbrains.jps.dependency.diff.Difference.Specifier

private object EmptyDiff : Specifier<Any, Difference> {
  override fun unchanged(): Boolean = true
}

@Suppress("UNCHECKED_CAST")
internal fun <T, D : Difference> emptyDiff(): Specifier<T, D> = EmptyDiff as Specifier<T, D>

internal fun <T : DiffCapable<T, D>, D : Difference> deepDiff(past: Collection<T>?, now: Collection<T>?): Specifier<T, D> {
  if (past.isNullOrEmpty()) {
    if (now.isNullOrEmpty()) {
      return emptyDiff()
    }
    else {
      return object : Specifier<T, D> {
        override fun added(): Iterable<T> = now

        override fun unchanged(): Boolean = false
      }
    }
  }
  else if (now.isNullOrEmpty()) {
    return object : Specifier<T, D> {
      override fun removed(): Iterable<T> = past

      override fun unchanged(): Boolean = false
    }
  }
  return deepDiffForSets(toSet(past), toSet(now))
}

private const val SET_THRESHOLD = 4

private fun <D : Difference, T : DiffCapable<T, D>> toSet(collection: Collection<T>): Collection<T> {
  return if (collection.size < SET_THRESHOLD || collection is Set<*>) collection else collection.toCollection(ObjectOpenHashSet(collection.size))
}

internal fun <D : Difference, T : DiffCapable<T, D>> deepDiffForSets(
  past: Collection<T>,
  now: Collection<T>,
): Specifier<T, D> {
  val pastSameSet = past.toCollection(ObjectOpenCustomHashSet(past.size, DiffCapableHashStrategy))
  val nowSameSet = now.toCollection(ObjectOpenCustomHashSet(now.size, DiffCapableHashStrategy))

  val added = computeCustomCollectionDiff(nowSameSet, pastSameSet)
  val removed = computeCustomCollectionDiff(pastSameSet, nowSameSet)
  return object : Specifier<T, D> {
    private var changed: List<Difference.Change<T, D>>? = null

    override fun added() = added

    override fun removed() = removed

    override fun changed(): List<Difference.Change<T, D>> {
      var changed = changed
      if (changed == null) {
        changed = computeChanged(pastSameSet, nowSameSet)
        this.changed = changed
      }
      return changed
    }

    override fun unchanged(): Boolean {
      return added.isEmpty() && removed.isEmpty() && changed().isEmpty()
    }
  }
}

internal fun <D : Difference, T : DiffCapable<T, D>> notLazyDeepDiffForSets(
  past: Collection<T>,
  now: ScatterSet<out T>,
): Specifier<T, D>? {
  val pastSameSet = past.toCollection(ObjectOpenCustomHashSet(past.size, DiffCapableHashStrategy))
  val nowSameSet = ObjectOpenCustomHashSet<T>(now.size, DiffCapableHashStrategy)
  now.forEach {
    nowSameSet.add(it)
  }

  val added = computeCustomCollectionDiff(nowSameSet, pastSameSet)
  val removed = computeCustomCollectionDiff(pastSameSet, nowSameSet)
  val changed = computeChanged(pastSameSet, nowSameSet)

  if (added.isEmpty() && removed.isEmpty() && changed.isEmpty()) {
    return null
  }

  return object : Specifier<T, D> {
    override fun added() = added

    override fun removed() = removed

    override fun changed() = changed

    override fun unchanged(): Boolean = false
  }
}

private fun <T> computeCustomCollectionDiff(a: ObjectOpenCustomHashSet<T>, b: ObjectOpenCustomHashSet<T>): Collection<T> {
  return when {
    a.size < SET_THRESHOLD -> a.filterTo(ArrayList()) { !b.contains(it) }
    else -> a.filterTo(ObjectOpenHashSet()) { !b.contains(it) }
  }
}

private fun <T : DiffCapable<T, D>, D : Difference> computeChanged(
  now: ObjectOpenCustomHashSet<T>,
  past: ObjectOpenCustomHashSet<T>,
): List<Difference.Change<T, D>> {
  val nowMap = Object2ObjectOpenCustomHashMap<T, T>(DiffCapableHashStrategy)
  for (s in now) {
    if (past.contains(s)) {
      nowMap.put(s, s)
    }
  }

  val changed = ArrayList<Difference.Change<T, D>>()
  for (before in past) {
    val after = nowMap.get(before) ?: continue
    val diff = after.difference(before)
    if (!diff.unchanged()) {
      changed.add(Difference.Change.create(before, after, diff))
    }
  }
  return changed
}

object DiffCapableHashStrategy : Strategy<DiffCapable<*, *>> {
  override fun hashCode(o: DiffCapable<*, *>?): Int = o?.diffHashCode() ?: 0

  override fun equals(a: DiffCapable<*, *>?, b: DiffCapable<*, *>?): Boolean {
    return when {
      a == null -> b == null
      b == null -> false
      a === b -> true
      else -> a.isSame(b)
    }
  }
}

internal fun <T> diff(past: Collection<T>?, now: Collection<T>?): Specifier<T, Difference> {
  if (past.isNullOrEmpty()) {
    if (now.isNullOrEmpty()) {
      @Suppress("UNCHECKED_CAST")
      return EmptyDiff as Specifier<T, Difference>
    }
    return object : Specifier<T, Difference> {
      override fun added() = now

      override fun unchanged() = false
    }
  }
  else if (now.isNullOrEmpty()) {
    return object : Specifier<T, Difference> {
      override fun removed() = past

      override fun unchanged() = false
    }
  }

  return diffForSets(past = past, now = now)
}

private fun <T> computeCollectionDiff(a: Collection<T>, b: Collection<T>): Collection<T> {
  return when {
    a is PersistentSet<T> -> a.removeAll(b)
    else -> a.filter { !b.contains(it) }
  }
}

internal fun <T> diffForSets(past: Collection<T>, now: Collection<T>): Specifier<T, Difference> {
  val added = computeCollectionDiff(now, past)
  val removed = computeCollectionDiff(past, now)
  return object : Specifier<T, Difference> {
    private var isUnchanged: Boolean? = null

    override fun added() = added

    override fun removed() = removed

    override fun unchanged(): Boolean {
      var isUnchanged = isUnchanged
      if (isUnchanged == null) {
        isUnchanged = past == now
        this.isUnchanged = isUnchanged
      }
      return isUnchanged
    }

    override fun changed(): Iterable<Difference.Change<T, Difference>> = emptySet()
  }
}

internal fun <T> notLazyDiffForSets(past: Collection<T>, now: ScatterSet<T>): Specifier<T, Difference> {
  val added = now.filterToList { !past.contains(it) }
  val removed = past.filter { !now.contains(it) }.ifEmpty { emptyList() }
  val isUnchanged = past == now
  return object : Specifier<T, Difference> {
    override fun added() = added

    override fun removed() = removed

    override fun unchanged(): Boolean = isUnchanged

    override fun changed(): Iterable<Difference.Change<T, Difference>> = emptySet()
  }
}