@file:JvmName("CharacterSequences")
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor

/**
 * Returns the code point at the specified index.
 *
 * If there is a full surrogate pair at the specified index, returns the code point corresponding to that pair.
 * Otherwise, simply returns the value of the character at the specified index without any further validity checks.
 *
 * @param index the index in the sequence, must be non-negative and less than the sequence length
 * @return the code point at the specified index
 */
fun CharSequence.codePointAt(index: Int): Int {
  val first = this[index]
  if (first.isHighSurrogate() && index + 1 < this.length) {
    val second = this[index + 1]
    if (second.isLowSurrogate()) {
      return Character.toCodePoint(first, second)
    }
  }
  return first.code
}
