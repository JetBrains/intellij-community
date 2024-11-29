// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.radixTrie

import fleet.util.serialization.DataSerializer
import fleet.util.reducible.ReduceDecision
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

private typealias TinyInt = Int // 5 bit

private typealias What = Int // None(0) | Value(1) | Collision(2)

private const val NONE: What = 0
private const val DATA: What = 1
private const val COLLISION: What = 2

typealias BitMap = Int

//TODO: specialize for Longs!
@Serializable(with=RadixTrieSerializer::class)
class RadixTrieNode<V : Any>(var ints: IntArray?, // [k1 k2 k3]
                             var buffer: Array<Any?>, // [v1 v2 v3 ... c3 c2 c1]
                             var dataMap: BitMap, // [0 0 1 0 0 0 1 0 0 1 .. 0]
                             var collisionsMap: BitMap,
                             val editor: Any) {

  companion object {
    val EMPTY = RadixTrieNode<Any>(ints = null,
                                   buffer = emptyArray(),
                                   dataMap = 0,
                                   collisionsMap = 0,
                                   editor = Any())

    fun <V : Any> empty(): RadixTrieNode<V> = EMPTY as RadixTrieNode<V>

    fun <V : Any> makeCollision(k1: Int, v1: V, k2: Int, v2: V, shift: Int, editor: Any): RadixTrieNode<V> = run {
      val tinyKey1 = tinyInt(k1, shift)
      val tinyKey2 = tinyInt(k2, shift)
      when {
        tinyKey1 == tinyKey2 -> {
          val child = makeCollision(k1, v1, k2, v2, shift + 5, editor)
          RadixTrieNode(ints = null,
                        buffer = arrayOf(child),
                        dataMap = 0,
                        collisionsMap = 0.setBit(tinyKey1),
                        editor = editor)
        }
        else -> {
          val dataMap = 0.setBit(tinyKey1).setBit(tinyKey2)
          if (tinyKey1 < tinyKey2) {
            RadixTrieNode(ints = intArrayOf(k1, k2),
                          buffer = arrayOf(v1, v2),
                          dataMap = dataMap,
                          collisionsMap = 0,
                          editor = editor)
          }
          else {
            RadixTrieNode(ints = intArrayOf(k2, k1),
                          buffer = arrayOf(v2, v1),
                          dataMap = dataMap,
                          collisionsMap = 0,
                          editor = editor)
          }
        }
      }
    }
  }

  fun reduce(reducer: (Int, V) -> ReduceDecision): ReduceDecision = run {
    val keysCount = dataMap.countOneBits()
    val ints = ints
    val objects = buffer
    repeat(keysCount) { dataIndex ->
      val key = ints!![dataIndex]
      val value = objects[dataIndex] as V
      if (reducer(key, value) == ReduceDecision.Stop) {
        return ReduceDecision.Stop
      }
    }
    val collisionsCount = collisionsMap.countOneBits()
    repeat(collisionsCount) { i ->
      val child = objects[objects.size - 1 - i] as RadixTrieNode<V>
      if (child.reduce(reducer) == ReduceDecision.Stop) {
        return ReduceDecision.Stop
      }
    }
    ReduceDecision.Continue
  }

  private inline fun mutate(editor: Any, body: RadixTrieNode<V>.() -> Unit): RadixTrieNode<V> =
    when {
      editor === this.editor -> this.apply { body() }
      else -> RadixTrieNode<V>(ints = ints?.copyOf(),
                               buffer = buffer.copyOf(),
                               dataMap = dataMap,
                               collisionsMap = collisionsMap,
                               editor = editor).apply { body() }
    }

  private fun what(key: TinyInt): What =
    when {
      dataMap.getBit(key) -> DATA
      collisionsMap.getBit(key) -> COLLISION
      else -> NONE
    }

  private fun collisionIndex(key: TinyInt): Int =
    buffer.size - 1 - collisionsMap.bitsBefore(key)

  private fun dataIndex(key: TinyInt): Int =
    dataMap.bitsBefore(key)

  fun getIntObject(key: Int, shift: Int): V? =
    tinyInt(key, shift).let { tinyKey ->
      when (what(tinyKey)) {
        NONE -> {
          null
        }
        DATA -> {
          val dataIndex = dataIndex(tinyKey)
          val existingKey = ints!![dataIndex] // could be ommitted for leaf nodes
          if (existingKey == key) {
            buffer[dataIndex] as V
          }
          else null
        }
        COLLISION -> {
          val collisionIndex = collisionIndex(tinyKey)
          val child = buffer[collisionIndex] as RadixTrieNode<V>
          child.getIntObject(key, shift + 5)
        }
        else -> error("unreachable")
      }
    }

  val isEmpty: Boolean
    get() = dataMap == 0 && collisionsMap == 0

  fun updateIntObject(key: Int, shift: Int, value: (V?) -> V?, editor: Any): RadixTrieNode<V> = run {
    val tinyKey = tinyInt(key, shift)
    when (what(tinyKey)) {
      NONE -> {
        val v = value(null)
        when {
          v == null -> {
            this
          }
          else -> {
            mutate(editor) {
              val dataIndex = dataIndex(tinyKey)
              dataMap = dataMap.setBit(tinyKey)
              ints = ints.insert(dataIndex, key)
              buffer = buffer.insert(dataIndex, v)
            }
          }
        }
      }
      DATA -> {
        val dataIndex = dataIndex(tinyKey)
        val existingKey = ints!![dataIndex]
        when {
          existingKey == key -> {
            val existingValue = buffer[dataIndex] as V
            val v = value(existingValue)
            when {
              v == null -> {
                mutate(editor) {
                  dataMap = dataMap.unsetBit(tinyKey)
                  buffer = buffer.remove(dataIndex)
                  ints = ints!!.remove(dataIndex)
                }
              }
              existingValue === v -> { // or should we just replace the old value? or at least check for reference equals? maybe the equality check is useful for the set semantics of the db
                this
              }
              else -> {
                mutate(editor) {
                  buffer[dataIndex] = v
                }
              }
            }
          }
          else -> {
            val v = value(null)
            when {
              v == null -> {
                this
              }
              else -> {
                val existingValue = buffer[dataIndex] as V
                val collisionIndex = collisionIndex(tinyKey)
                mutate(editor) {
                  dataMap = dataMap.unsetBit(tinyKey)
                  collisionsMap = collisionsMap.setBit(tinyKey)
                  ints = ints!!.remove(dataIndex)
                  val child = makeCollision(key, v,
                                            existingKey, existingValue,
                                            shift + 5, editor)
                  buffer.replaceDataWithCollision(dataIndex, collisionIndex, child)
                }
              }
            }
          }
        }
      }
      COLLISION -> {
        val collisionIndex = collisionIndex(tinyKey)
        val child = buffer[collisionIndex] as RadixTrieNode<V>
        val childPrime = child.updateIntObject(key, shift + 5, value, editor)
        when {
          child === childPrime -> {
            this
          }
          childPrime.collisionsMap == 0 && childPrime.dataMap.countOneBits() == 1 -> {
            val childKey = childPrime.ints!![0]
            val childValue = childPrime.buffer[0]
            val dataIndex = dataIndex(tinyKey)
            mutate(editor) {
              collisionsMap = collisionsMap.unsetBit(tinyKey)
              dataMap = dataMap.setBit(tinyKey)
              ints = ints.insert(dataIndex, childKey)
              buffer.replaceCollisionWithData(dataIndex, collisionIndex, childValue)
            }
          }
          else -> {
            mutate(editor) {
              buffer[collisionIndex] = childPrime
            }
          }
        }
      }
      else -> error("unreachable")
    }
  }
}

typealias RadixTrie<T> = RadixTrieNode<T>
typealias RadixTrieLong = RadixTrieNode<Long>

inline fun <V : Any> RadixTrie<V>.forEach(crossinline f: (Int, V) -> Unit) {
  reduce { i, v -> f(i, v); ReduceDecision.Continue }
}

operator fun <V : Any> RadixTrie<V>.get(key: Int): V? = getIntObject(key, 0)
fun <V : Any> RadixTrie<V>.remove(editor: Any?, key: Int): RadixTrie<V> = update(editor, key) { null }
fun <V : Any> RadixTrie<V>.put(editor: Any?, key: Int, v: V): RadixTrie<V> = update(editor, key) { v }
fun <V : Any> RadixTrie<V>.update(editor: Any?, key: Int, value: (V?) -> V?): RadixTrie<V> = updateIntObject(key, 0, value, editor ?: Any())

class RadixTrieSerializer<V : Any>(valSer: KSerializer<V>) : DataSerializer<RadixTrie<V>, Map<Int, V>>(MapSerializer(Int.serializer(), valSer)) {
  override fun fromData(data: Map<Int, V>): RadixTrie<V> {
    val editor = Any()
    return data.entries.fold(RadixTrie.empty()) { trie, (k, v) ->
      trie.put(editor, k, v)
    }
  }

  override fun toData(value: RadixTrie<V>): Map<Int, V> {
    val map = mutableMapOf<Int, V>()
    value.forEach { k, v -> map[k] = v }
    return map
  }
}

private fun Array<Any?>.replaceCollisionWithData(dataIndex: Int, collisionIndex: Int, data: Any?) {
  if (dataIndex < collisionIndex) {
    copyInto(destination = this,
             startIndex = dataIndex,
             endIndex = collisionIndex,
             destinationOffset = dataIndex + 1)
  }
  this[dataIndex] = data
}

private fun Array<Any?>.replaceDataWithCollision(dataIndex: Int, collisionIndex: Int, collision: RadixTrieNode<*>) {
  if (dataIndex < collisionIndex) {
    copyInto(destination = this,
             startIndex = dataIndex + 1,
             endIndex = collisionIndex + 1,
             destinationOffset = dataIndex)
  }
  this[collisionIndex] = collision
}

private fun Array<Any?>.insert(index: Int, value: Any?): Array<Any?> {
  val res = arrayOfNulls<Any?>(size + 1)
  copyInto(destination = res,
           startIndex = 0,
           endIndex = index,
           destinationOffset = 0)
  res[index] = value
  copyInto(destination = res,
           startIndex = index,
           endIndex = size,
           destinationOffset = index + 1)
  return res
}

private fun Array<Any?>.remove(index: Int): Array<Any?> =
  when {
    this.size == 1 -> {
      emptyArray()
    }
    else -> {
      val res = arrayOfNulls<Any?>(size - 1)
      copyInto(destination = res,
               startIndex = 0,
               endIndex = index,
               destinationOffset = 0)
      copyInto(destination = res,
               startIndex = index + 1,
               endIndex = size,
               destinationOffset = index)
      res
    }
  }

private fun IntArray?.insert(index: Int, value: Int): IntArray =
  when {
    this == null -> {
      intArrayOf(value)
    }
    else -> {
      val res = IntArray(size + 1)
      copyInto(destination = res,
               startIndex = 0,
               endIndex = index,
               destinationOffset = 0)
      res[index] = value
      copyInto(destination = res,
               startIndex = index,
               endIndex = size,
               destinationOffset = index + 1)
      res
    }
  }

private fun IntArray.remove(index: Int): IntArray? =
  when {
    this.size == 1 -> {
      null
    }
    else -> {
      val res = IntArray(size - 1)
      copyInto(destination = res,
               startIndex = 0,
               endIndex = index,
               destinationOffset = 0)
      copyInto(destination = res,
               startIndex = index + 1,
               endIndex = size,
               destinationOffset = index)
      res
    }
  }


private inline fun mask(key: TinyInt): Int =
  1 shl key

private inline fun tinyInt(key: Int, shift: Int): TinyInt =
  (key shr shift) and 31

private inline fun BitMap.setBit(bit: Int): BitMap =
  mask(bit) or this

private inline fun BitMap.unsetBit(bit: Int): BitMap =
  mask(bit) xor this

private inline fun BitMap.getBit(bit: Int): Boolean =
  (mask(bit) and this) != 0

private inline fun BitMap.bitsBefore(key: TinyInt): Int =
  (this and (mask(key) - 1)).countOneBits()

private fun radix() {
  val e = Any()
  var t = RadixTrie.empty<String>()
  var v: String? = null
  repeat(10000000) { i ->
    v = "hey $i"
    t = t.update(e, i) { v!! }
  }
  repeat(10000000) { i ->
    require(t[i] == "hey $i")
  }
  val kvs = HashMap<Int, Any?>()
  t.reduce { k, v ->
    kvs[k] = v
    ReduceDecision.Continue
  }
  repeat(10000000) { i ->
    require(kvs[i] == "hey $i")
  }
  repeat(10000000) { i ->
    t = t.update(e, i) { null }
  }
  repeat(10000000) { i ->
    require(t[i] == null)
  }
  t.reduce { k, v ->
    error("should be empty: $k")
  }
}
