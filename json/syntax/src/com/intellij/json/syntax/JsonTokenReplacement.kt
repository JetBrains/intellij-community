// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.syntax

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.lexer.Lexer
import com.intellij.platform.syntax.syntaxElementTypeSetOf
import org.jetbrains.annotations.ApiStatus

/**
 * Checks that [lexer] reads [newText] as one token of [tokenType] between the token [prefix] and the text [suffix].
 * Then the leaf of the token can get the new text without a reparse of the file.
 *
 * A JSON lexer has one state, so the tokens of a text depend only on the text.
 * A token can change its neighbours only when it merges with them.
 * So the check lexes the previous token, the new text and the start of the next token together.
 * It requires a token boundary before and after the new text.
 * A block comment without its end can take the tokens after the next token, so the check refuses it.
 *
 * @param prefix the full text of the token before the leaf, or an empty text at the file start
 * @param suffix the first [SUFFIX_LOOKAHEAD] chars of the token after the leaf, or an empty text at the file end
 */
@ApiStatus.Internal
fun canReplaceJsonToken(
  lexer: Lexer,
  tokenType: SyntaxElementType,
  prefix: CharSequence,
  newText: CharSequence,
  suffix: CharSequence,
): Boolean {
  if (tokenType !in REPLACEABLE_TOKENS || newText.isEmpty()) return false
  if (tokenType === JsonSyntaxElementTypes.BLOCK_COMMENT && (newText.length < 4 || !newText.endsWith("*/"))) return false
  val text = StringBuilder(prefix.length + newText.length + suffix.length).append(prefix).append(newText).append(suffix)
  val start = prefix.length
  val end = start + newText.length
  lexer.start(text)
  while (true) {
    val type = lexer.getTokenType() ?: return false
    val tokenStart = lexer.getTokenStart()
    if (tokenStart > start) return false
    if (tokenStart == start) return type === tokenType && lexer.getTokenEnd() == end
    lexer.advance()
  }
}

/**
 * The chars of the next token that [canReplaceJsonToken] needs to find the end of the new token.
 * The lexer finds the end of a token when it reads a char that cannot continue the token.
 * Then it goes back to the last accepted end.
 * In a JSON5 `+Infinity`, the chars after `+` have no accepted end until the last char.
 * This is the longest such run in a token, so 64 chars are enough.
 */
const val SUFFIX_LOOKAHEAD: Int = 64

/** The tokens whose leaf can get a new text. A keyword cannot: `true` with a new char is an identifier. */
private val REPLACEABLE_TOKENS = syntaxElementTypeSetOf(
  JsonSyntaxElementTypes.DOUBLE_QUOTED_STRING,
  JsonSyntaxElementTypes.SINGLE_QUOTED_STRING,
  JsonSyntaxElementTypes.NUMBER,
  JsonSyntaxElementTypes.IDENTIFIER,
  JsonSyntaxElementTypes.LINE_COMMENT,
  JsonSyntaxElementTypes.BLOCK_COMMENT,
)
