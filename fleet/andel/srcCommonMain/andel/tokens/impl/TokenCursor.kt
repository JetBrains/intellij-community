// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.tokens.impl

import andel.rope.Metric
import andel.rope.Rope

internal data class TokenCursor(
  val cursor: Rope.Cursor<TokenArray>,
  val startOffset: Int,
  val endOffset: Int,
  val startTokenIndex: Int,
  val endTokenIndex: Int,
  val tokens: TokenArray,
) {

  fun next(owner: Any?): TokenCursor? =
    cursor.next(owner)?.asTokenCursor()

  fun scan(metric: Metric, value: Int): TokenCursor =
    when {
      metric == TokenMonoid.TokenCount && startTokenIndex <= value && value < endTokenIndex -> this
      metric == TokenMonoid.CharCount && startOffset <= value && value < endOffset -> this
      else -> cursor.scan(Any(), metric, value).let { cur ->
        when {
          cur === cursor -> this
          else -> cur.asTokenCursor()
        }
      }
    }
}

internal fun Rope.Cursor<TokenArray>.asTokenCursor(): TokenCursor = let { cur ->
  val startTokenIndex = cur.location(TokenMonoid.TokenCount)
  val startOffset = cur.location(TokenMonoid.CharCount)
  val tokenCount = cur.size(TokenMonoid.TokenCount)
  val charCount = cur.size(TokenMonoid.CharCount)
  TokenCursor(
    cursor = cur,
    startTokenIndex = startTokenIndex,
    endTokenIndex = startTokenIndex + tokenCount,
    startOffset = startOffset,
    endOffset = startOffset + charCount,
    tokens = cur.element
  )
}     
