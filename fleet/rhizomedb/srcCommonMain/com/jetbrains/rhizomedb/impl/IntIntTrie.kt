// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rhizomedb.impl

import fleet.util.radixTrie.*

internal typealias IntIntTrie<V> = RadixTrie<RadixTrie<V>>

internal fun <V : Any> IntIntTrie<V>.get(key1: Int, key2: Int): V? = get(key1)?.get(key2)

internal fun <V : Any> IntIntTrie<V>.update(editor: Editor, key1: Int, key2: Int, update: (V?) -> V?): IntIntTrie<V> =
  update(editor, key1) { t ->
    when {
      t == null -> update(null)?.let { v ->
        RadixTrie.empty<V>().update(editor, key2) { v }
      }
      else -> t.update(editor, key2, update).takeIf { !it.isEmpty }
    }
  }

internal fun <V : Any> IntIntTrie<V>.put(editor: Editor, key1: Int, key2: Int, v: V): IntIntTrie<V> =
  update(editor, key1) { t -> (t ?: RadixTrie.empty()).update(editor, key2) { v } }

internal fun <V : Any> IntIntTrie<V>.remove(editor: Editor, key1: Int, key2: Int): IntIntTrie<V> =
  update(editor, key1) { t -> t?.remove(editor, key2) }
