// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.suggested

import com.intellij.openapi.util.TextRange

fun TextRange.stripWhitespace(chars: CharSequence): TextRange =
  strip(chars) { it.isWhitespace() }

fun TextRange.strip(chars: CharSequence, predicate: (Char) -> Boolean): TextRange {
  require(startOffset >= 0)
  require(endOffset <= chars.length)

  var startOffset = startOffset
  while (startOffset < endOffset && predicate(chars[startOffset])) {
    startOffset++
  }

  var endOffset = endOffset
  while (endOffset > startOffset && predicate(chars[endOffset - 1])) {
    endOffset--
  }

  return TextRange(startOffset, endOffset)
}


fun TextRange.extendWithWhitespace(chars: CharSequence): TextRange {
  return extend(chars) { it.isWhitespace() }
}

fun TextRange.extend(chars: CharSequence, predicate: (Char) -> Boolean): TextRange {
  require(startOffset >= 0)
  require(endOffset <= chars.length)

  var startOffset = this.startOffset
  while (startOffset > 0 && predicate(chars[startOffset - 1])) {
    startOffset--
  }

  var endOffset = this.endOffset
  while (endOffset < chars.length && predicate(chars[endOffset])) {
    endOffset++
  }

  return TextRange(startOffset, endOffset)
}
