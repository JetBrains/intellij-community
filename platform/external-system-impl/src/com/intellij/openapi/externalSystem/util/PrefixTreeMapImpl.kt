// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.util

import com.intellij.util.containers.FList
import gnu.trove.THashMap
import gnu.trove.TObjectHashingStrategy

open class PrefixTreeMapImpl<K, V>(private val strategy: TObjectHashingStrategy<K>? = null) : PrefixTreeMap<K, V> {

  private val root = Node()

  override operator fun get(path: List<K>) = root.get(path.toFList())?.getValue()

  override operator fun set(path: List<K>, value: V) = root.put(path.toFList(), value)

  override fun remove(path: List<K>) = root.remove(path.toFList())

  override fun contains(path: List<K>) = root.contains(path.toFList())

  override fun getAllDescendants(path: List<K>) = root.get(path.toFList())?.getAllDescendants() ?: emptyList()

  private inner class Node {
    private val children = strategy?.let { THashMap<K, Node>(it) } ?: THashMap()
    private val isLeaf get() = children.isEmpty
    private val isInvalid get() = isLeaf && !isPresent
    private var isPresent: Boolean = false
    private var value: V? = null

    fun getValue() = value

    fun put(path: FList<K>, value: V): V? {
      val (head, tail) = path
      val child = children.getOrPut(head) { Node() }
      if (tail.isEmpty()) {
        val previousValue = child.value
        child.value = value
        child.isPresent = true
        return previousValue
      }
      else return child.put(tail, value)
    }

    fun remove(path: FList<K>): V? {
      val (head, tail) = path
      val child = children[head] ?: return null
      if (tail.isEmpty()) {
        val value = child.value
        child.value = null
        child.isPresent = false
        return value
      }
      else {
        val result = child.remove(tail)
        if (child.isInvalid) {
          children.remove(head)
        }
        return result
      }
    }

    fun contains(path: FList<K>): Boolean {
      val (head, tail) = path
      val child = children[head] ?: return false
      return when {
        tail.isEmpty() -> child.isPresent
        else -> child.contains(tail)
      }
    }

    fun get(path: FList<K>): Node? {
      val (head, tail) = path
      val child = children[head] ?: return null
      return when {
        tail.isEmpty() -> child
        else -> child.get(tail)
      }
    }

    fun getAllDescendants(): List<V> {
      val result = children.flatMap { it.value.getAllDescendants() }.toMutableList()
      value?.let { result.add(it) }
      return result
    }
  }

  companion object {
    private operator fun <E> FList<E>.component1() = head

    private operator fun <E> FList<E>.component2() = tail

    private fun <E> List<E>.toFList() = FList.createFromReversed(asReversed())
  }
}