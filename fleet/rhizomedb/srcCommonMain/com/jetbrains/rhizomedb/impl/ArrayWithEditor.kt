// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.rhizomedb.impl

internal class ArrayWithEditor<T>(
  private val editor: Editor,
  private val array: Array<T>
) {
  
  companion object {
    fun<T> withArray(array: Array<T>): ArrayWithEditor<T> =
      ArrayWithEditor(Editor(), array)
  }

  override fun toString(): String =
    array.contentToString()

  val size: Int
    get() = array.size

  operator fun get(index: Int): T =
    array[index]

  // inline!
  inline fun update(editor: Editor, index: Int, f: (T) -> T): ArrayWithEditor<T> = let { self ->
    val oldValue = self.array[index]
    val newValue = f(oldValue)
    when {
      newValue == oldValue -> self
      editor === self.editor -> {
        array[index] = newValue
        self
      }
      else -> {
        val copy = array.copyOf()
        copy[index] = newValue
        ArrayWithEditor(editor, copy)
      }
    }
  }
}