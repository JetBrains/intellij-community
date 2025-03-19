// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rhizomedb

import com.jetbrains.rhizomedb.impl.Editor
import kotlinx.collections.immutable.*
import fleet.util.removeShim

internal data class EAT(val e: EID, val a: Attribute<*>, val t: TX)

internal fun Datom.eat(): EAT = EAT(e = eid, a = attr, t = tx)

class MutableNovelty(
  private var novelty: Novelty = Novelty.Empty,
  private var editor: Editor = Editor(),
) : Collection<Datom> {

  override fun contains(element: Datom): Boolean = novelty.contains(element)

  override fun containsAll(elements: Collection<Datom>): Boolean = elements.all { contains(it) }

  fun addAll(other: Iterable<Datom>) {
    novelty = other.fold(novelty) { novelty, datom ->
      novelty.add(editor, datom)
    }
  }

  fun add(datom: Datom) {
    novelty = novelty.add(editor, datom)
  }

  fun persistent(): Novelty = novelty.also { editor = Editor() }


  override val size: Int get() = novelty.size

  override fun isEmpty(): Boolean = novelty.isEmpty()

  override fun iterator(): Iterator<Datom> = novelty.iterator()
}

/**
 * Novelty represents the difference between two DB snapshots: dbBefore and dbAfter.
 * Every retracted datom in Novelty is guaranteed to be present in dbBefore,
 * Every added datom in Novelty is guaranteed to be present in dbAfter.
 * */
class Novelty internal constructor(
  internal val assertions: PersistentMap.Builder<EAT, Any>,
  internal val retractions: PersistentMap.Builder<EAT, Any>,
  internal val editor: Editor,
  internal var _size: Int,
) : ImmutableCollection<Datom> {

  companion object {
    val Empty = Novelty(assertions = persistentHashMapOf<EAT, Any>().builder(),
                        retractions = persistentHashMapOf<EAT, Any>().builder(),
                        editor = Editor(),
                        _size = 0)
  }

  override fun contains(element: Datom): Boolean =
    when (element.added) {
      true -> assertions.containsKey(element.eat())
      false -> retractions.containsKey(element.eat())
    }

  fun assertions(): Sequence<Datom> = sequence {
    for ((eat, v) in assertions) {
      when (eat.a.schema.cardinality) {
        Cardinality.One -> yield(Datom(eat.e, eat.a, v, eat.t, true))
        Cardinality.Many -> {
          for (v1 in (v as SetWithEditor).set) {
            yield(Datom(eat.e, eat.a, v1, eat.t, true))
          }
        }
      }
    }
  }

  fun retractions(): Sequence<Datom> = sequence {
    for ((eat, v) in retractions) {
      when (eat.a.schema.cardinality) {
        Cardinality.One -> yield(Datom(eat.e, eat.a, v, eat.t, false))
        Cardinality.Many -> {
          for (v1 in (v as SetWithEditor).set) {
            yield(Datom(eat.e, eat.a, v1, eat.t, false))
          }
        }
      }
    }
  }

  override fun containsAll(elements: Collection<Datom>): Boolean = elements.all { contains(it) }

  override val size: Int
    get() = _size

  override fun isEmpty(): Boolean = assertions.isEmpty() && retractions.isEmpty()

  override fun iterator(): Iterator<Datom> =
    iterator {
      for ((eat, v) in retractions) {
        when (eat.a.schema.cardinality) {
          Cardinality.One -> yield(Datom(eat.e, eat.a, v, eat.t, false))
          Cardinality.Many -> {
            for (v1 in (v as SetWithEditor).set) {
              yield(Datom(eat.e, eat.a, v1, eat.t, false))
            }
          }
        }
      }
      for ((eat, v) in assertions) {
        when (eat.a.schema.cardinality) {
          Cardinality.One -> yield(Datom(eat.e, eat.a, v, eat.t, true))
          Cardinality.Many -> {
            for (v1 in (v as SetWithEditor).set) {
              yield(Datom(eat.e, eat.a, v1, eat.t, true))
            }
          }
        }
      }
    }

  fun mutable(): MutableNovelty =
    MutableNovelty(this)

  operator fun plus(rhs: Novelty): Novelty =
    mutable().apply { addAll(rhs) }.persistent()

  operator fun unaryMinus(): Novelty =
    Novelty(assertions = retractions,
            retractions = assertions,
            editor = Editor(),
            _size = _size)

  private inline fun withMutableCopy(editor: Editor, body: Novelty.() -> Unit): Novelty =
    when {
      editor == this.editor -> this
      else -> Novelty(
        assertions = assertions.build().builder(),
        retractions = retractions.build().builder(),
        editor = editor,
        _size = _size
      )
    }.apply(body)


  /**
   * retractions and assertions must not be reordered!
   * the method is low level and requires care, please consider using Novelty.plus instead
   * */
  fun add(editor: Editor = Editor(), datom: Datom): Novelty =
    withMutableCopy(editor) {
      // to maintain the invariant of Novelty we have to omit any intermediate states:
      val eat = datom.eat()
      when (datom.added) {
        true -> {
          if (!retractions.removeCardinalityAware(editor, eat, datom.value)) {
            // the datom has not been retracted during this transaction, list it as a new assertion:
            assertions.addCardinalityAware(editor, eat, datom.value)
            _size++
          }
          else {
            _size--
            // normally one can't assert the same datom that was retracted already, because the T is unique,
            // however this might happen during the process of union with an inverted novelty
          }
        }
        false -> {
          if (!assertions.removeCardinalityAware(editor, eat, datom.value)) {
            // the datom has not been added during this transaction, list it as a new retraction:
            retractions.addCardinalityAware(editor, eat, datom.value)
            _size++
          }
          else {
            _size--
            // the datom has been added and retracted within the same tx,
            // meaning it must not be listed in the novelty,
            // as it violates the property of Novelty
          }
        }
      }
    }
}

private class SetWithEditor(val set: PersistentSet.Builder<Any>,
                            val editor: Editor) {
  fun add(editor: Editor, any: Any): SetWithEditor =
    when {
      editor == this.editor -> this.also { set.add(any) }
      else -> SetWithEditor(set.build().builder().apply { add(any) }, editor)
    }

  // returns null if it was not there
  fun remove(editor: Editor, any: Any): SetWithEditor? =
    when {
      editor == this.editor -> if (set.remove(any)) this else null
      else -> {
        val mut = set.build().builder()
        if (mut.remove(any)) SetWithEditor(mut, editor) else null
      }
    }
}

// true if removed
private fun MutableMap<EAT, Any>.removeCardinalityAware(editor: Editor, eat: EAT, value: Any): Boolean =
  when (eat.a.schema.cardinality) {
    Cardinality.One -> removeShim(eat, value)
    Cardinality.Many -> {
      when (val set = this[eat]) {
        null -> {
          false
        }
        else -> {
          val setPrime = (set as SetWithEditor).remove(editor, value)
          when {
            setPrime == null -> false
            setPrime.set.isEmpty() -> {
              remove(eat)
              true
            }
            else -> true
          }
        }
      }
    }
  }

private fun MutableMap<EAT, Any>.addCardinalityAware(editor: Editor, eat: EAT, value: Any) {
  when (eat.a.schema.cardinality) {
    Cardinality.One -> {
      put(eat, value)
    }
    Cardinality.Many -> {
      val set = this[eat]
      when {
        set == null -> {
          put(eat, SetWithEditor(persistentHashSetOf(value).builder(), editor))
        }
        else -> {
          val newSet = (set as SetWithEditor).add(editor, value)
          if (newSet !== set) {
            put(eat, newSet)
          }
        }
      }
    }
  }
}

fun Iterable<Datom>.toNovelty(): Novelty =
  when (this) {
    is Novelty -> this
    is MutableNovelty -> persistent()
    else -> MutableNovelty().apply { addAll(this@toNovelty) }.persistent()
  }

fun EAVa.display(): String = "[$eid ${DbContext.threadBound.displayAttribute(attr)}, $value, $added]"


fun Novelty.deduplicateValues(): Iterable<EAVa> = let { novelty ->
  val assertions = novelty.assertions().map { d -> EAV(d.eid, d.attr, d.value) }.toHashSet()
  val retractions = novelty.retractions().map { d -> EAV(d.eid, d.attr, d.value) }.toHashSet()
  (retractions.asSequence().filter { eav -> eav !in assertions }.map { EAVa(it.eid, it.attr, it.value, false) } +
   assertions.asSequence().filter { eav -> eav !in retractions }.map { EAVa(it.eid, it.attr, it.value, true) }).toList()
}

