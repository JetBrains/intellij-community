// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentAspect
import com.intellij.openapi.editor.ex.DocumentAspectList
import com.intellij.openapi.util.Key
import com.intellij.util.ArrayUtil

internal class DocumentAspectListImpl private constructor(
  // Invariant: keys are sorted Key.hashCode() indices; values is parallel to keys; neither array is mutated in place
  private val keys: IntArray,
  private val values: Array<DocumentAspect>,
) : DocumentAspectList {

  private constructor() : this(intArrayOf(), emptyArray())

  override fun get(key: Key<out DocumentAspect>): DocumentAspect? {
    val index = indexOf(key.hashCode())
    if (index < 0) {
      return null
    }
    return values[index]
  }

  override fun add(key: Key<out DocumentAspect>, aspect: DocumentAspect): DocumentAspectList {
    val keyCode = key.hashCode()
    val index = indexOf(keyCode)
    if (index >= 0) {
      if (values[index] === aspect) {
        return this
      }
      val newValues = values.copyOf()
      newValues[index] = aspect
      return DocumentAspectListImpl(keys, newValues)
    }
    val insertionIndex = -index - 1
    val newKeys = ArrayUtil.insert(keys, insertionIndex, keyCode)
    val newValues = ArrayUtil.insert(values, insertionIndex, aspect)
    return DocumentAspectListImpl(newKeys, newValues)
  }

  override fun remove(key: Key<out DocumentAspect>): DocumentAspectList {
    val index = indexOf(key.hashCode())
    if (index < 0) {
      return this
    }
    if (keys.size == 1) {
      return EMPTY
    }
    val newKeys = ArrayUtil.remove(keys, index)
    val newValues = ArrayUtil.remove(values, index)
    return DocumentAspectListImpl(newKeys, newValues)
  }

  override fun transform(action: (DocumentAspect) -> DocumentAspect): DocumentAspectList {
    var newValues: Array<DocumentAspect>? = null
    for (i in values.indices) {
      val aspect = values[i]
      val newAspect = action(aspect)
      if (newAspect === aspect) {
        continue
      }
      if (newValues == null) {
        newValues = values.copyOf()
      }
      newValues[i] = newAspect
    }
    if (newValues == null) {
      return this
    }
    return DocumentAspectListImpl(keys, newValues)
  }

  /**
   * @return index of [keyCode] in [keys] or `-insertionIndex - 1` if absent, same contract as [java.util.Arrays.binarySearch].
   * Linear scan with early exit over the sorted keys is fine because the list is expected to be small
   */
  private fun indexOf(keyCode: Int): Int {
    for (i in keys.indices) {
      val key = keys[i]
      if (key == keyCode) {
        return i
      }
      if (key > keyCode) {
        return -i - 1
      }
    }
    return -keys.size - 1
  }

  companion object {
    val EMPTY = DocumentAspectListImpl()
  }
}
