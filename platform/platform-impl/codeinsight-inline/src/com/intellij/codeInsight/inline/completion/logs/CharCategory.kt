// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class CharCategory(val chars: CharSequence) {
  LETTER(('a'..'z').joinToString("")),
  CAPITAL_LETTER(('A'..'Z').joinToString("")),
  UNDERSCORE("_"),
  NUMBER(('0'..'9').joinToString("")),
  QUOTE("\"\'`"),
  OPENING_BRACKET("([{"),
  CLOSING_BRACKET(")]}"),
  SIGN("=+-></\\|"),
  PUNCTUATION(".,:;?!"),
  SYMBOL("#$%&*^@~");

  companion object {
    fun find(char: Char): CharCategory? = entries.find { it.chars.contains(char) }
  }
}