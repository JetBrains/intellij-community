@file:Suppress("SSBasedInspection")

package org.jetbrains.jps.dependency.java

import it.unimi.dsi.fastutil.Hash.Strategy
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import jdk.internal.org.jline.utils.Colors.s
import org.jetbrains.jps.dependency.diff.DiffCapable
import org.jetbrains.jps.dependency.diff.Difference
import org.jetbrains.jps.dependency.diff.Difference.Specifier

private object EmptyDiff : Specifier<Any, Difference> {
  override fun unchanged(): Boolean = true
}

internal fun <T : DiffCapable<T, D>, D : Difference> deepDiff(past: Collection<T>?, now: Collection<T>?): Specifier<T, D> {
  if (past.isNullOrEmpty()) {
    if (now.isNullOrEmpty()) {
      @Suppress("UNCHECKED_CAST")
      return EmptyDiff as Specifier<T, D>
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

  val pS = toSet(past)
  val nS = toSet(now)

  val added = now.asSequence().filter { !pS.contains(it) }.asIterable()
  val removed = past.asSequence().filter { !nS.contains(it) }.asIterable()

  val nowMap = Object2ObjectOpenCustomHashMap<T, T>(DiffCapableHashStrategy)
  for (s in now) {
    if (past.contains(s)) {
      nowMap.put(s, s)
    }
  }

  val changed by lazy(LazyThreadSafetyMode.NONE) {
    val changed = ArrayList<Difference.Change<T, D>>()
    for (before in past) {
      val after = nowMap.get(before) ?: continue
      val diff = after.difference(before)!!
      if (!diff.unchanged()) {
        changed.add(Difference.Change.create(before, after, diff))
      }
    }
    changed
  }

  return object : Specifier<T, D> {
    override fun added(): Iterable<T> = added

    override fun removed(): Iterable<T> = removed

    override fun changed(): Iterable<Difference.Change<T, D>> = changed
  }
}

private fun <D : Difference, T : DiffCapable<T, D>> toSet(past: Collection<T>): Collection<T> {
  return if (past.size < 4 || past is Set<*>) past else past.toCollection(ObjectOpenHashSet(past.size))
}

internal object DiffCapableHashStrategy : Strategy<DiffCapable<*, *>> {
  override fun hashCode(o: DiffCapable<*, *>?): Int = o?.diffHashCode() ?: 0

  override fun equals(a: DiffCapable<*, *>?, b: DiffCapable<*, *>?): Boolean {
    return when {
      a == null -> b == null
      b == null -> false
      else -> a === b || a.isSame(b)
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
      override fun added() = now.asIterable()

      override fun unchanged() = false
    }
  }
  else if (now.isNullOrEmpty()) {
    return object : Specifier<T, Difference> {
      override fun removed() = past.asIterable()

      override fun unchanged() = false
    }
  }

  val pastSet = past.toCollection(ObjectOpenHashSet())
  val nowSet = now.toCollection(ObjectOpenHashSet())

  val added = nowSet.asSequence().filter { !pastSet.contains(it) }.asIterable()
  val removed = pastSet.asSequence().filter { !nowSet.contains(it) }.asIterable()

  return object : Specifier<T, Difference> {
    private var isUnchanged: Boolean? = null
    override fun added() = added

    override fun removed(): Iterable<T> = removed

    override fun unchanged(): Boolean {
      var isUnchanged = isUnchanged
      if (isUnchanged == null) {
        isUnchanged = pastSet == nowSet
        this.isUnchanged = isUnchanged
      }
      return isUnchanged
    }
  }
}