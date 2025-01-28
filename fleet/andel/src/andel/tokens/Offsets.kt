// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.tokens

/**
 * Replaces tokens by offsets
 *
 * First and last tokens in range may be shortened if offset split them in a parts
 */
public fun <T> Tokens<T>.replaceTokensInRange(fromOffset: Int, toOffset: Int, newTokens: List<Token2<T>>): Tokens<T> =
  mutate {
    it.replaceTokensInRange(fromOffset, toOffset, newTokens)
  }

/**
 * Replaces tokens by offsets
 *
 * First and last tokens in range may be shortened if offset split them in a parts
 */
public fun <T> MutableTokensView<T>.replaceTokensInRange(fromOffset: Int, toOffset: Int, newTokens: List<Token2<T>>) {
  require(fromOffset <= toOffset) { "$fromOffset > $toOffset" }

  if (tokenCount == 0) {
    replaceTokens(0.tokenIndex, 0.tokenIndex, newTokens)
    return
  }

  val startOffset = fromOffset.coerceIn(0, charCount)
  val endOffset = toOffset.coerceIn(0, charCount)

  if (startOffset == endOffset) {
    // Nothing to remove
    val idx = tokenIndexAtOffset(startOffset)
    val tokenStart = tokenStart(idx)
    val tokenEnd = tokenEnd(idx)

    if (startOffset == tokenStart) {
      // Nothing to remove, we before token
      replaceTokens(idx, idx, newTokens)
    }
    else if (startOffset == tokenEnd) {
      // Nothing to remove, we after token
      replaceTokens(idx + 1, idx + 1, newTokens)
    }
    else {
      // We need to split token
      val tokenType = tokenType(idx)
      val isEdited = isEdited(idx)
      val isRestartable = isRestartable(idx)
      replaceTokens(idx, idx + 1, buildList {
        add(Token2(length = startOffset - tokenStart, type = tokenType, edited = isEdited, restartable = isRestartable))
        addAll(newTokens)
        add(Token2(length = tokenEnd - startOffset, type = tokenType, edited = isEdited, restartable = isRestartable))
      })
    }
  }
  else {
    val fromIdx = tokenIndexAtOffset(startOffset)
    val toIdx = tokenIndexAtOffset(endOffset - 1)

    // Take first token prefix, if needed
    val prefixToken = run {
      val fromStartOffset = tokenStart(fromIdx)
      if (fromStartOffset < startOffset) {
        Token2(
          length = startOffset - fromStartOffset,
          type = tokenType(fromIdx),
          edited = isEdited(fromIdx),
          restartable = isRestartable(fromIdx),
        )
      }
      else {
        null
      }
    }

    // Take last token suffix, if needed
    val postfixToken = run {
      val toEndOffset = tokenEnd(toIdx)
      if (endOffset < toEndOffset) {
        Token2(
          length = toEndOffset - endOffset,
          type = tokenType(toIdx),
          edited = isEdited(toIdx),
          restartable = isRestartable(toIdx),
        )
      }
      else {
        null
      }
    }

    if (prefixToken == null && postfixToken == null) {
      replaceTokens(fromIdx, toIdx + 1, newTokens)
    }
    else {
      val newTokensList = buildList {
        prefixToken?.let(::add)
        addAll(newTokens)
        postfixToken?.let(::add)
      }
      replaceTokens(fromIdx, toIdx + 1, newTokensList)
    }
  }
}