// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rhizomedb.impl

import com.jetbrains.rhizomedb.Attribute
import com.jetbrains.rhizomedb.EID
import fleet.fastutil.ints.Int2ObjectOpenHashMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf

class Editor

class MapWithEditor<K : Any, V : Any>(val editor: Editor,
                                      var map: PersistentMap.Builder<K, V>) {
  companion object {
    fun <K : Any, V : Any> keyValue(editor: Editor, key: K, value: V): MapWithEditor<K, V> =
      MapWithEditor(editor, persistentHashMapOf<K, V>().builder().apply { put(key, value) })

    fun <K : Any, V : Any> empty(editor: Editor): MapWithEditor<K, V> =
      MapWithEditor(editor, persistentHashMapOf<K, V>().builder())
  }

  fun put(editor: Editor, key: K, v: V): MapWithEditor<K, V>? =
    update(editor, key) { v }

  fun remove(editor: Editor, key: K): MapWithEditor<K, V>? =
    update(editor, key) { null }

  inline fun update(editor: Editor, key: K, f: (V?) -> V?): MapWithEditor<K, V>? {
    val fork = maybeForked(editor)
    val v = fork.map.get(key)
    val vPrime = f(v)
    return fork.replaceKey(key, v, vPrime)
  }

  fun maybeForked(editor: Editor): MapWithEditor<K, V> =
    when {
      editor === this.editor -> this
      else -> MapWithEditor(editor, map.build().builder())
    }

  fun replaceKey(key: K, v: V?, vPrime: V?): MapWithEditor<K, V>? = let { fork ->
    when {
      vPrime === v -> fork
      else ->
        when (vPrime) {
          null -> {
            fork.map.remove(key)
            when {
              fork.map.size == 0 -> null
              else -> fork
            }
          }
          else -> {
            fork.map.put(key, vPrime)
            fork
          }
        }
    }
  }
}

class IntMapWithEditor<V : Any>(val editor: Editor,
                                val map: Int2ObjectOpenHashMap<V>) {

  companion object Companion {
    fun <V : Any> keyValue(editor: Editor, key: EID, value: V): IntMapWithEditor<V> =
      IntMapWithEditor(editor,
                       Int2ObjectOpenHashMap<V>(1).apply { put(key, value) })

    fun <V : Any> empty(editor: Editor): IntMapWithEditor<V> =
      IntMapWithEditor(editor, Int2ObjectOpenHashMap(0))
  }

  fun get(attribute: Attribute<*>): V? = map.get(attribute.eid)

  inline fun update(editor: Editor, key: Int, f: (V?) -> V?): IntMapWithEditor<V>? {
    val fork = maybeForked(editor)
    val v = fork.map.get(key)
    val vPrime = f(v)
    return fork.replaceKey(key, v, vPrime)
  }

  fun maybeForked(editor: Editor): IntMapWithEditor<V> =
    when {
      editor === this.editor -> this
      else -> IntMapWithEditor(editor, Int2ObjectOpenHashMap(map))
    }

  fun replaceKey(key: Int,
                 v: V?,
                 vPrime: V?): IntMapWithEditor<V>? = let { fork ->
    when {
      vPrime === v -> fork
      else ->
        when (vPrime) {
          null -> {
            fork.map.remove(key)
            when {
              fork.map.isEmpty() -> null
              else -> fork
            }
          }
          else -> {
            fork.map.put(key, vPrime)
            fork
          }
        }
    }
  }
}