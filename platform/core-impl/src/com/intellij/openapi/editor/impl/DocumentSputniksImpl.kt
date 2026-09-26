// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.ex.DocumentOp
import com.intellij.openapi.editor.ex.DocumentSnapshot
import com.intellij.openapi.editor.ex.DocumentSputnik
import com.intellij.openapi.editor.ex.DocumentSputniks
import com.intellij.openapi.editor.ex.DocumentTextPatch
import com.intellij.openapi.util.Key
import com.intellij.util.ArrayUtil

internal class DocumentSputniksImpl private constructor(
  // Invariant: keys are sorted Key.hashCode() indices; values is parallel to keys; neither array is mutated in place
  private val keys: IntArray,
  private val values: Array<DocumentSputnik>,
) : DocumentSputniks {

  private constructor() : this(intArrayOf(), emptyArray())

  override fun get(key: Key<out DocumentSputnik>): DocumentSputnik? {
    val index = indexOf(key.hashCode())
    if (index < 0) {
      return null
    }
    return values[index]
  }

  override fun applyOp(
    before: DocumentSnapshot,
    after: DocumentSnapshot,
    op: DocumentOp,
    nextSnapshot: (DocumentSputniks) -> DocumentSnapshot,
  ): DocumentSnapshot {
    return when (op) {
      is DocumentTextPatch -> if (before.text() === after.text()) after else applyTextPatch(before, after, op, nextSnapshot)
      is DocumentOp.SetSputnik -> applySetSputnik(after, op, nextSnapshot)
      is DocumentOp.ModStamp,
      is DocumentOp.UnmodifiedLines -> after
    }
  }

  private fun applyTextPatch(
    before: DocumentSnapshot,
    after: DocumentSnapshot,
    patch: DocumentTextPatch,
    nextSnapshot: (DocumentSputniks) -> DocumentSnapshot,
  ): DocumentSnapshot {
    var result = after
    var currentSputniks = this
    for (i in values.indices) {
      val sputnik = values[i]
      val newSputnik = sputnik.applyOp(before, result, patch)
      if (newSputnik === sputnik) {
        continue
      }
      val newValues = currentSputniks.values.copyOf()
      newValues[i] = newSputnik
      currentSputniks = DocumentSputniksImpl(keys, newValues)
      result = nextSnapshot.invoke(currentSputniks)
    }
    return result
  }

  private fun applySetSputnik(
    snapshot: DocumentSnapshot,
    op: DocumentOp.SetSputnik,
    nextSnapshot: (DocumentSputniks) -> DocumentSnapshot,
  ): DocumentSnapshot {
    val key = op.key()
    val sputnik = op.sputnik()
    val sputniks = if (sputnik == null) {
      remove(key)
    } else {
      add(key, sputnik)
    }
    if (sputniks === this) {
      return snapshot
    }
    return nextSnapshot.invoke(sputniks)
  }

  private fun add(key: Key<out DocumentSputnik>, sputnik: DocumentSputnik): DocumentSputniks {
    val keyCode = key.hashCode()
    val index = indexOf(keyCode)
    if (index >= 0) {
      if (values[index] === sputnik) {
        return this
      }
      val newValues = values.copyOf()
      newValues[index] = sputnik
      return DocumentSputniksImpl(keys, newValues)
    }
    val insertionIndex = -index - 1
    val newKeys = ArrayUtil.insert(keys, insertionIndex, keyCode)
    val newValues = ArrayUtil.insert(values, insertionIndex, sputnik)
    return DocumentSputniksImpl(newKeys, newValues)
  }

  private fun remove(key: Key<out DocumentSputnik>): DocumentSputniks {
    val index = indexOf(key.hashCode())
    if (index < 0) {
      return this
    }
    if (keys.size == 1) {
      return EMPTY
    }
    val newKeys = ArrayUtil.remove(keys, index)
    val newValues = ArrayUtil.remove(values, index)
    return DocumentSputniksImpl(newKeys, newValues)
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
    val EMPTY = DocumentSputniksImpl()
  }
}
