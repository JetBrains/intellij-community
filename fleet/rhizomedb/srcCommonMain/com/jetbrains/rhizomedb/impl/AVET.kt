// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rhizomedb.impl

import com.jetbrains.rhizomedb.*
import fleet.util.radixTrie.*
import kotlin.jvm.JvmInline

/**
 * the last Any here is either Map<EID, TX> or Versioned<T> depending on unique?
 * */
@JvmInline
internal value class AVET(private val trie: IntMapWithEditor<MapWithEditor<Any, Any>>) {
  companion object {
    fun empty(editor: Editor): AVET = AVET(IntMapWithEditor.empty(editor))
  }

  fun <T : Any> lookupUnique(attribute: Attribute<T>, value: T): VersionedEID? =
    trie.get(attribute)?.map?.get(value) as VersionedEID?

  inline fun <T : Any> lookupMany(attribute: Attribute<T>, value: T, crossinline sink: (Datom) -> Unit) {
    trie.get(attribute)?.map?.get(value)?.let { es ->
      (es as RadixTrie<TX>).forEach { e, t ->
        sink(Datom(e, attribute, value, t, true))
      }
    }
  }

  fun remove(editor: Editor, eid: EID, attribute: Attribute<*>, value: Any): AVET =
    when {
      attribute.schema.unique -> removeOne(editor, attribute.eid, value)
      else -> removeMany(editor, attribute.eid, value, eid)
    }

  fun add(editor: Editor, eid: EID, attribute: Attribute<*>, valueToAdd: Any, tx: TX): AVET =
    when {
      attribute.schema.unique -> addOne(editor, attribute.eid, valueToAdd, eid, tx)
      else -> addMany(editor, attribute.eid, valueToAdd, eid, tx)
    }

  private fun addOne(editor: Editor, a: Int, v: Any, e: EID, tx: TX): AVET =
    trie.update(editor, a) { vet ->
      when (vet) {
        null -> MapWithEditor.keyValue(editor, v, VersionedEID(e, tx))
        else ->
          (vet as MapWithEditor<Any, VersionedEID>).put(editor, v, VersionedEID(e, tx))
      } as MapWithEditor<Any, Any>
    }?.let { AVET(it) } ?: empty(editor)

  private fun addMany(editor: Editor, a: Int, v: Any, e: EID, t: TX): AVET =
    trie.update(editor, a) { vets ->
      when {
        vets == null -> MapWithEditor.keyValue(editor, v, RadixTrie.empty<TX>().put(editor, e, t))
        else ->
          (vets as MapWithEditor<Any, RadixTrie<TX>>).update(editor, v) { mv2 ->
            (mv2 ?: RadixTrie.empty()).put(editor, e, t)
          } as MapWithEditor<Any, Any>
      }
    }?.let { AVET(it) } ?: empty(editor)

  private fun removeOne(editor: Editor, a: Int, v: Any): AVET =
    trie.update(editor, a) { evt ->
      (evt as MapWithEditor<Any, VersionedEID>?)?.remove(editor, v) as MapWithEditor<Any, Any>?
    }?.let { AVET(it) } ?: empty(editor)

  private fun removeMany(editor: Editor, a: Int, v: Any, e: EID): AVET =
    trie.update(editor, a) { evts ->
      (evts as MapWithEditor<Any, RadixTrie<TX>>?)?.update(editor, v) { mv2 ->
        mv2?.remove(editor, e)?.takeUnless { it.isEmpty }
      } as MapWithEditor<Any, Any>?
    }?.let { AVET(it) } ?: empty(editor)
}

