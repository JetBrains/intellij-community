// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rhizomedb.impl

import com.jetbrains.rhizomedb.*
import fleet.util.reducible.ReduceDecision

internal data class DBSet<T : Any>(val eid: EID,
                                   val attr: Attribute<Any>) : MutableSet<T> {

  fun getValue(t: T): Any {
    return if (attr.schema.isRef) (t as Entity).eid else t
  }

  override fun add(element: T): Boolean =
    DbContext.threadBound.ensureMutable {
      mutate(Add(eid, attr, getValue(element), generateSeed())).isNotEmpty()
    }

  override fun addAll(elements: Collection<T>): Boolean =
    DbContext.threadBound.ensureMutable {
      var changed = false
      for (e in elements) {
        val added = mutate(Add(eid, attr, getValue(e), generateSeed())).isNotEmpty()
        changed = added || changed
      }
      changed
    }

  override fun clear() {
    DbContext.threadBound.ensureMutable {
      mutate(RetractAttribute(eid = eid,
                              attribute = attr,
                              seed = generateSeed()))
    }
  }

  override fun iterator(): MutableIterator<T> {
    with(DbContext.threadBound) {
      val context = this
      val s = queryIndex(IndexQuery.GetMany(eid, attr))
      val iter = s.iterator()
      return object : MutableIterator<T> {
        var currentValue: Any? = null
        var currentValueForRemove: Any? = null
        var cachedEntity: Any? = null

        override fun hasNext(): Boolean {
          if (currentValue != null) return true
          while (iter.hasNext()) {
            if (attr.schema.isRef) {
              val nextValue = iter.next().value as EID
              cachedEntity = entity(nextValue)
              if (cachedEntity != null) {
                currentValue = nextValue
                return true
              }
            } else {
              currentValue = iter.next().value
              return true
            }
          }
          return false
        }

        override fun next(): T {
          if (currentValue == null) {
            if (!hasNext()) {
              throw NoSuchElementException()
            }
          }

          return ((if (attr.schema.isRef) cachedEntity else currentValue) as T).also {
            currentValueForRemove = currentValue
            currentValue = null
          }
        }

        override fun remove() {
          with(context as DbContext<Mut>) {
            currentValueForRemove?.let {
              mutate(Remove(eid = eid,
                            attribute = attr,
                            value = it,
                            seed = generateSeed()))
            } ?: throw NoSuchElementException()
          }
        }
      }
    }
  }

  override fun remove(element: T): Boolean {
    return DbContext.threadBound.ensureMutable {
      mutate(Remove(eid, attr, getValue(element), generateSeed())).isNotEmpty()
    }
  }

  override fun removeAll(elements: Collection<T>): Boolean {
    var changed = false
    for (e in elements) {
      val removed = remove(e)
      changed = removed || changed
    }
    return changed
  }

  override fun retainAll(elements: Collection<T>): Boolean {
    val es = if (attr.schema.isRef) {
      elements.mapTo(HashSet()) { e -> (e as Entity).eid }
    }
    else {
      elements
    }
    return DbContext.threadBound.ensureMutable {
      var changed = false
      queryIndex(IndexQuery.GetMany(eid, attr))
        .forEach { (_, _, v) ->
          if (!es.contains(v)) {
            val removed = mutate(Remove(eid = eid,
                                        attribute = attr,
                                        value = v,
                                        seed = generateSeed())).isNotEmpty()
            changed = removed || changed
          }
          ReduceDecision.Continue
        }
      changed
    }
  }

  override val size: Int
    get() = DbContext.threadBound.impl.queryIndex(IndexQuery.GetMany(eid, attr)).count()

  override fun contains(element: T): Boolean {
    return DbContext.threadBound.impl.queryIndex(IndexQuery.Contains(eid, attr, getValue(element))) != null
  }

  override fun containsAll(elements: Collection<T>): Boolean {
    val q = DbContext.threadBound.impl
    return if (attr.schema.isRef) {
      elements.all { e -> q.queryIndex(IndexQuery.Contains(eid, attr, (e as Entity).eid)) != null }
    }
    else {
      elements.all { e -> q.queryIndex(IndexQuery.Contains(eid, attr, e)) != null }
    }
  }

  override fun isEmpty(): Boolean {
    return !DbContext.threadBound.impl.queryIndex(IndexQuery.GetMany(eid, attr)).any()
  }
}