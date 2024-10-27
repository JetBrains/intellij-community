// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rhizomedb.impl

import com.jetbrains.rhizomedb.*

/**
 * the last Any here is either Radix<TX> or VersionedEID depending on unique?
 * */
@JvmInline
internal value class VAET(private val trie: RadixTrie<IntMapWithEditor<Any>>) {

  companion object {
    fun empty(): VAET = VAET(RadixTrie.empty())
  }

  fun remove(editor: Editor, eid: EID, attribute: Attribute<*>, value: Int): VAET =
    when {
      attribute.schema.unique -> removeOne(editor, value, attribute.eid, eid)
      else -> removeMany(editor, value, attribute.eid, eid)
    }

  fun add(editor: Editor, eid: EID, attribute: Attribute<*>, valueToAdd: Int, tx: TX): VAET =
    when {
      attribute.schema.unique -> addOne(editor, valueToAdd, attribute.eid, eid, tx)
      else -> addMany(editor, valueToAdd, attribute.eid, eid, tx)
    }

  private fun addOne(editor: Editor, v: Int, a: Int, e: EID, t: TX): VAET =
    VAET(trie.update(editor, v) { aet ->
      when (aet) {
        null -> IntMapWithEditor.keyValue(editor, a, VersionedEID(e, t))
        else -> aet.update(editor, a) { VersionedEID(e, t) }
      }
    })

  private fun addMany(editor: Editor, v: Int, a: Int, e: EID, t: TX): VAET =
    VAET(trie.update(editor, v) { aet ->
      when (aet) {
        null -> IntMapWithEditor.keyValue(editor, a, RadixTrie.empty<TX>().put(editor, e, t))
        else -> aet.update(editor, a) { et -> (et as RadixTrie<TX>? ?: RadixTrie.empty()).put(editor, e, t) }
      }
    })

  private fun removeOne(editor: Editor, v: Int, a: Int, e: EID): VAET =
    VAET(trie.update(editor, v) { aet ->
      aet?.update(editor, a) { et -> null }
    })

  private fun removeMany(editor: Editor, v: Int, a: Int, e: EID): VAET =
    VAET(trie.update(editor, v) { aet ->
      aet?.update(editor, a) { et ->
        (et as RadixTrie<TX>?)?.remove(editor, e)?.takeUnless { it.isEmpty }
      }
    })

  inline fun refsTo(v: EID, crossinline sink: (Datom) -> Unit) {
    trie.get(v)?.let { aet ->
      aet.map.forEach { (attr, value) ->
        val a = Attribute<Any>(attr)
        when {
          a.schema.unique -> {
            val et = value as VersionedEID
            sink(Datom(et.eid, a, v, et.tx))
          }
          else -> {
            (value as RadixTrie<TX>).reduce { e, t ->
              sink(Datom(e, a, v, t))
              ReduceDecision.Continue
            }
          }
        }
        ReduceDecision.Continue
      }
    }
  }

  fun lookupUnique(value: EID, attribute: Attribute<*>): VersionedEID? =
    trie.get(value)?.get(attribute) as VersionedEID?

  inline fun lookupMany(value: EID, attribute: Attribute<*>, crossinline sink: (Datom) -> Unit) {
    (trie.get(value)?.get(attribute) as RadixTrie<TX>?)?.forEach { e, t ->
      sink(Datom(e, attribute, value, t, true))
    }
  }
}