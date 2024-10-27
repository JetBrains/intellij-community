// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rhizomedb.impl

import com.jetbrains.rhizomedb.*

/**
 * last Any here is either Map<T, TX> or Versioned<T> depending on Cardinality
 * */
@JvmInline
internal value class AEVT(private val trie: IntMapWithEditor<RadixTrie<Any>>) {
  companion object {
    fun empty(editor: Editor): AEVT = AEVT(IntMapWithEditor.empty(editor))
  }

  data class AdditionResult(val aevt: AEVT,
                            val removedValue: Versioned<Any>?,
                            val added: Boolean)

  data class RemovalResult(val aevt: AEVT,
                           val removed: TX?)

  fun <T : Any> getOne(eid: EID, attribute: Attribute<T>): Versioned<T>? =
    trie.get(attribute)?.get(eid)?.let { vt ->
      when (attribute.schema.isRef) {
        true -> (vt as VersionedEID?)?.let { (v, t) -> Versioned(v as T, t) }
        false -> vt as Versioned<T>
      }
    }

  fun entityExists(eid: EID): Boolean =
    trie.get(Entity.Type.attr as Attribute<EID>)?.get(eid) != null

  inline fun <T : Any> getMany(eid: EID, attribute: Attribute<T>, crossinline sink: (Datom) -> Unit) {
    when (attribute.schema.cardinality) {
      Cardinality.Many ->
        trie.get(attribute)?.get(eid)?.let { values ->
          when (attribute.schema.isRef) {
            true ->
              (values as RadixTrieLong).reduce { v, t ->
                sink(Datom(eid, attribute, v, t, true))
                ReduceDecision.Continue
              }
            false ->
              (values as MapWithEditor<Any, TX>).map.forEach { (value, tx) ->
                sink(Datom(eid, attribute, value, tx, true))
              }
          }
        }
      Cardinality.One ->
        getOne(eid, attribute)?.let { versioned ->
          sink(Datom(eid, attribute, versioned.x, versioned.tx))
        }
    }
  }

  fun contains(eid: EID, attr: Attribute<*>, value: Any): TX? =
    when (attr.schema.cardinality) {
      Cardinality.One ->
        trie.get(attr)?.get(eid)?.let { versioned ->
          when (attr.schema.isRef) {
            true -> (versioned as VersionedEID).takeIf { it.eid == value }?.tx
            false -> (versioned as Versioned<*>).takeIf { it.x == value }?.tx
          }
        }
      Cardinality.Many ->
        trie.get(attr)?.get(eid)?.let { vt ->
          when (attr.schema.isRef) {
            true -> (vt as RadixTrieLong).get(value as EID)
            false -> (vt as MapWithEditor<Any, TX>).map.get(value)
          }
        }
    }

  fun all(): Sequence<Datom> = trie.let { aevt ->
    buildList {
      aevt.map.forEach { (a, evt) ->
        val attr = Attribute<Any>(a)
        when (attr.schema.cardinality) {
          Cardinality.One ->
            when (attr.schema.isRef) {
              true -> evt.reduce { e, vt ->
                vt as VersionedEID
                add(Datom(e, attr, vt.eid, vt.tx))
                ReduceDecision.Continue
              }
              false -> evt.reduce { e, vt ->
                vt as Versioned<Any>
                add(Datom(e, attr, vt.x, vt.tx))
                ReduceDecision.Continue
              }
            }
          Cardinality.Many ->
            when (attr.schema.isRef) {
              true -> evt.reduce { e, vt ->
                (vt as RadixTrieLong).reduce { v, t ->
                  add(Datom(e, attr, v, t))
                  ReduceDecision.Continue
                }
              }
              false -> evt.reduce { e, vt ->
                (vt as MapWithEditor<Any, TX>).map.forEach { (v, t) ->
                  add(Datom(e, attr, v, t))
                }
                ReduceDecision.Continue
              }
            }
        }
      }
    }.asSequence()
  }

  fun remove(editor: Editor, eid: EID, attribute: Attribute<*>, value: Any): RemovalResult =
    when (attribute.schema.cardinality) {
      Cardinality.One ->
        when (attribute.schema.isRef) {
          true -> removeOneEID(editor, attribute.eid, eid)
          false -> removeOneAny(editor, attribute.eid, eid)
        }

      Cardinality.Many ->
        when (attribute.schema.isRef) {
          true -> removeManyEID(editor, attribute.eid, eid, value as EID)
          false -> removeManyAny(editor, attribute.eid, eid, value)
        }
    }

  fun add(editor: Editor, eid: EID, attribute: Attribute<*>, valueToAdd: Any, tx: TX): AdditionResult =
    when (attribute.schema.cardinality) {
      Cardinality.One ->
        when (attribute.schema.isRef) {
          true -> addOneEID(editor, attribute.eid, eid, valueToAdd as EID, tx)
          false -> addOneAny(editor, attribute.eid, eid, valueToAdd, tx)
        }
      Cardinality.Many ->
        when (attribute.schema.isRef) {
          true -> addManyEID(editor, attribute.eid, eid, valueToAdd as EID, tx)
          false -> addManyAny(editor, attribute.eid, eid, valueToAdd, tx)
        }
    }

  inline fun column(attribute: Attribute<*>, crossinline sink: (Datom) -> Unit) {
    trie.get(attribute)?.let { evt ->
      when (attribute.schema.cardinality) {
        Cardinality.One ->
          when (attribute.schema.isRef) {
            true -> evt.reduce { e, vt ->
              vt as VersionedEID
              sink(Datom(e, attribute, vt.eid, vt.tx))
              ReduceDecision.Continue
            }
            false -> evt.reduce { e, vt ->
              vt as Versioned<Any>
              sink(Datom(e, attribute, vt.x, vt.tx))
              ReduceDecision.Continue
            }
          }
        Cardinality.Many ->
          when (attribute.schema.isRef) {
            true -> evt.reduce { e, vt ->
              vt as RadixTrieLong
              vt.reduce { v, t ->
                sink(Datom(e, attribute, v, t))
                ReduceDecision.Continue
              }
            }
            false -> evt.reduce { e, vt ->
              vt as MapWithEditor<Any, TX>
              for ((v, t) in vt.map) {
                sink(Datom(e, attribute, v, t))
              }
              ReduceDecision.Continue
            }
          }
      }
    }
  }

  private fun addOneAny(editor: Editor, a: Int, e: EID, v: Any, t: TX): AdditionResult = run {
    var removedValue: Versioned<Any>? = null
    var added = false
    val aevtPrime: AEVT? = trie.update(editor, a) { evt ->
      (evt ?: RadixTrie.empty()).update(editor, e) { oldVersionedValue ->
        oldVersionedValue as Versioned<Any>?
        when {
          oldVersionedValue == null -> Versioned(v, t).also { added = true }
          oldVersionedValue.x == v -> oldVersionedValue.also { added = false }
          else -> Versioned(v, t).also {
            removedValue = oldVersionedValue
            added = true
          }
        }
      }
    }?.let { AEVT(it) }
    AdditionResult(aevtPrime ?: empty(editor), removedValue, added)
  }


  private fun addOneEID(editor: Editor, a: Int, e: EID, v: EID, t: TX): AdditionResult = run {
    var removedValue: Versioned<Any>? = null
    var added = false
    val aevtPrime: AEVT? = trie.update(editor, a) { evt ->
      (evt ?: RadixTrie.empty()).update(editor, e) { oldVersionedValue ->
        oldVersionedValue as VersionedEID?
        when {
          oldVersionedValue == null -> VersionedEID(v, t).also { added = true }
          oldVersionedValue.eid == v -> oldVersionedValue.also { added = false }
          else -> VersionedEID(v, t).also {
            removedValue = Versioned(oldVersionedValue.eid, oldVersionedValue.tx)
            added = true
          }
        }
      }
    }?.let { AEVT(it) }
    AdditionResult(aevtPrime ?: empty(editor), removedValue, added)
  }

  private fun addManyEID(editor: Editor, a: Int, e: EID, v: EID, t: TX): AdditionResult = run {
    var added = false
    val aevtPrime = trie.update(editor, a) { evt ->
      (evt ?: RadixTrie.empty()).update(editor, e) { mv2 ->
        when {
          mv2 == null -> RadixTrieLong.empty<Long>().put(editor, v, t).also { added = true }
          else -> (mv2 as RadixTrieLong).update(editor, v) { oldT ->
            when (oldT) {
              null -> t.also { added = true }
              else -> oldT.also { added = false }
            }
          }
        }
      }
    }?.let { AEVT(it) }
    AdditionResult(aevtPrime ?: empty(editor), null, added)
  }

  private fun addManyAny(editor: Editor, a: Int, e: Int, v: Any, t: TX): AdditionResult = run {
    var added = false
    val aevtPrime = trie.update(editor, a) { evt ->
      (evt ?: RadixTrie.empty()).update(editor, e) { mv2 ->
        when {
          mv2 == null -> MapWithEditor.keyValue(editor, v, t).also { added = true }
          else -> (mv2 as MapWithEditor<Any, TX>).update(editor, v) { oldT ->
            when (oldT) {
              null -> t.also { added = true }
              else -> oldT.also { added = false }
            }
          }
        }
      }
    }?.let { AEVT(it) }
    AdditionResult(aevtPrime ?: empty(editor), null, added)
  }

  private fun removeOneEID(editor: Editor, a: Int, e: Int): RemovalResult = run {
    var removed: TX? = null
    val aevtPrime = trie.update(editor, a) { evt ->
      when (evt) {
        null -> null.also { removed = null }
        else -> evt.update(editor, e) { vt ->
          removed = (vt as VersionedEID?)?.tx
          null
        }
      }
    }?.let { AEVT(it) }
    RemovalResult(aevtPrime ?: empty(editor), removed)
  }

  private fun removeOneAny(editor: Editor, a: Int, e: Int): RemovalResult = run {
    var removed: TX? = null
    val aevtPrime = trie.update(editor, a) { evt ->
      when (evt) {
        null -> null.also { removed = null }
        else -> evt.update(editor, e) { vt ->
          removed = (vt as Versioned<*>?)?.tx
          null
        }
      }
    }?.let { AEVT(it) }
    RemovalResult(aevtPrime ?: empty(editor), removed)
  }

  private fun removeManyAny(editor: Editor, a: Int, e: Int, v: Any): RemovalResult = run {
    var removed: TX? = null
    val aevtPrime = trie.update(editor, a) { evt ->
      evt?.update(editor, e) { vt ->
        (vt as MapWithEditor<Any, TX>?)?.update(editor, v) { oldT ->
          removed = oldT
          null
        }
      }
    }?.let { AEVT(it) }
    RemovalResult(aevtPrime ?: empty(editor), removed)
  }

  private fun removeManyEID(editor: Editor, a: Int, e: Int, v: EID): RemovalResult = run {
    var removed: TX? = null
    val aevtPrime = trie.update(editor, a) { evt ->
      evt?.update(editor, e) { vt ->
        (vt as RadixTrieLong?)?.update(editor, v) { oldT ->
          removed = oldT
          null
        }
      }
    }?.let { AEVT(it) }
    RemovalResult(aevtPrime ?: empty(editor), removed)
  }
}
