// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.ide.ui.search

import com.intellij.openapi.util.NlsSafe
import com.intellij.util.ArrayUtil
import it.unimi.dsi.fastutil.HashCommon
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet

/**
 * Converts the String to the int id and back.
 * It guarantees to return the same id for the equals Strings.
 * Used for compression: instead of keeping many (maybe equal) String references, it is sometimes cheaper to keep int ids in a packed array.
 */
internal class IndexedCharsInterner {
  private val set: StringSet = StringSet()

  fun toId(name: String): Int {
    var index = set.index(name)
    val id: Int
    if (index == -1) {
      id = set.size
      set.add(name)
      index = set.index(name)
      assert(index != -1)
      if (id >= set.idToIndex.size) {
        set.idToIndex = ArrayUtil.realloc(set.idToIndex, set.idToIndex.size * 3 / 2)
      }
      set.idToIndex[id] = index
      assert(set.idToIndex.indexOf(index) == id)
      assert(toId(name) == id)
    }
    else {
      id = set.idToIndex.indexOf(index)
    }
    assert(fromId(id) == name)
    return id
  }

  @NlsSafe
  fun fromId(id: Int): @NlsSafe String {
    assert(id >= 0 && id < set.size)
    return set.getByIndex(set.idToIndex[id])
  }
}

private class StringSet : ObjectOpenHashSet<String?>(10, 0.9f) {
  @JvmField var idToIndex = IntArray(10)

  fun getByIndex(index: Int): String {
    val set: Array<out Any?> = this.key
    return set[index] as String
  }

  override fun rehash(newCapacity: Int) {
    val oldSet: Array<out Any?> = this.key
    val oldSize = size

    super.rehash(newCapacity)

    for (i in 0..<oldSize - 1) {
      val oldIndex = idToIndex.get(i)
      val oldString = oldSet[oldIndex] as String
      val newIndex = index(oldString)
      assert(newIndex != -1)
      idToIndex[i] = newIndex
    }
  }

  fun index(k: String): Int {
    var curr: Any?
    val key: Array<out Any?> = this.key
    var pos: Int
    if ((key[(HashCommon.mix(k.hashCode()) and mask).also { pos = it }].also { curr = it }) == null) {
      return -1
    }
    if (k == curr) {
      return pos
    }

    // there's always an unused entry
    while (true) {
      if ((key[(pos + 1 and mask).also { pos = it }].also { curr = it }) == null) {
        return -1
      }
      if (k == curr) {
        return pos
      }
    }
  }
}