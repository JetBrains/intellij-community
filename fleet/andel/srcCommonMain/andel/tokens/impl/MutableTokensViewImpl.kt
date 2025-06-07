// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.tokens.impl

import andel.rope.Metric
import andel.tokens.*
import kotlin.math.min

internal class MutableTokensViewImpl<T>(
  var cursor: TokenCursor,
  val typeMap: TypeMapBuilder<T>,
  override var charCount: Int,
  override var tokenCount: Int,
  override var editCount: Int,
  override var restartableStateCount: Int,
) : MutableTokensView<T> {

  fun moveTo(metric: Metric, value: Int): TokenCursor =
    cursor.scan(metric, value).also {
      cursor = it
    }

  override fun tokenType(index: TokenIndex): T {
    val cur = moveTo(TokenMonoid.TokenCount, index.tokenIndex)
    val leafStartTokenIndex = cur.startTokenIndex
    val tokenArray = cur.tokens
    val typeId = tokenArray.typeId(index.tokenIndex - leafStartTokenIndex)
    return typeMap[typeId]
  }

  override fun tokenStart(index: TokenIndex): Int {
    val cur = moveTo(TokenMonoid.TokenCount, index.tokenIndex)
    val leafStartTokenIndex = cur.startTokenIndex
    val leafStartOffset = cur.startOffset
    val tokenArray = cur.tokens
    return tokenArray.start(index.tokenIndex - leafStartTokenIndex) + leafStartOffset
  }

  override fun tokenEnd(index: TokenIndex): Int {
    val cur = moveTo(TokenMonoid.TokenCount, index.tokenIndex)
    val leafStartTokenIndex = cur.startTokenIndex
    val leafStartOffset = cur.startOffset
    val tokenArray = cur.tokens
    return tokenArray.end(index.tokenIndex - leafStartTokenIndex) + leafStartOffset
  }

  override fun isRestartable(index: TokenIndex): Boolean {
    val cur = moveTo(TokenMonoid.TokenCount, index.tokenIndex)
    val leafStartTokenIndex = cur.startTokenIndex
    return cur.tokens.isRestartable(index.tokenIndex - leafStartTokenIndex)
  }

  override fun isEdited(index: TokenIndex): Boolean {
    val cur = moveTo(TokenMonoid.TokenCount, index.tokenIndex)
    val leafStartTokenIndex = cur.startTokenIndex
    return cur.tokens.isEdited(index.tokenIndex - leafStartTokenIndex)
  }

  override fun restartableStateCountBefore(index: TokenIndex): Int {
    val cur = moveTo(TokenMonoid.TokenCount, index.tokenIndex)
    val leafStartTokenIndex = cur.startTokenIndex
    val leafStartRestartableStateCount = cur.cursor.location(TokenMonoid.RestartableStateCount)
    return cur.tokens.restartableStateCountBefore(index.tokenIndex - leafStartTokenIndex) + leafStartRestartableStateCount
  }

  override fun tokenIndexAtOffset(offset: Int): TokenIndex {
    val cur = moveTo(TokenMonoid.CharCount, offset)
    val leafStartTokenIndex = cur.startTokenIndex
    val leafStartOffset = cur.startOffset
    val tokens = cur.tokens
    return (tokens.indexByOffset(offset - leafStartOffset) + leafStartTokenIndex).tokenIndex
  }

  override fun tokenIndexAtRestartableStateIndex(restartableStateIndex: Int): TokenIndex {
    val cur = moveTo(TokenMonoid.RestartableStateCount, restartableStateIndex)
    val leafStartTokenIndex = cur.startTokenIndex
    val leafStartRestartableStateCount = cur.cursor.location(TokenMonoid.RestartableStateCount)
    val tokens = cur.tokens
    return (tokens.indexAtRestartableStateIndex(restartableStateIndex - leafStartRestartableStateCount) + leafStartTokenIndex).tokenIndex
  }

  override fun tokenIndexAtEditIndex(editIndex: Int): TokenIndex {
    val cur = moveTo(TokenMonoid.EditCount, editIndex)
    val leafStartTokenIndex = cur.startTokenIndex
    val leafStartEditCount = cur.cursor.location(TokenMonoid.EditCount)
    val tokens = cur.tokens
    return (tokens.indexAtEditIndex(editIndex - leafStartEditCount) + leafStartTokenIndex).tokenIndex
  }

  override fun tokens(): Tokens<T> =
    Tokens(cursor.cursor.rope(Any()), typeMap.build())

  override fun replaceTokens(from: TokenIndex, to: TokenIndex, newToken2s: List<Token2<T>>) {
    val cur = moveTo(TokenMonoid.TokenCount, from.tokenIndex)
    val tokens = cur.tokens
    val leafStartTokenIndex = cur.startTokenIndex
    val count = to.tokenIndex - from.tokenIndex
    val leafFrom = from.tokenIndex - leafStartTokenIndex
    val leafTo = min(tokens.tokenCount, count + leafFrom)
    val tokensPrime = tokens.replaceTokens(
      fromIndex = leafFrom,
      toIndex = leafTo,
      tokens = tokenArray(newToken2s.size) {
        newToken2s.forEach { t ->
          add(
            typeId = typeMap.typeId(t.type),
            length = t.length,
            restartable = t.restartable,
            edited = t.edited
          )
        }
      }
    )
    val replaced = leafTo - leafFrom
    var remaining = count - replaced
    val owner = Any()
    var cursor = cur.cursor.replace(owner, tokensPrime).asTokenCursor()
    while (remaining > 0) {
      val next = cursor.next(owner)!!
      val leaf = next.tokens
      val toIndex = min(remaining, leaf.tokenCount)
      val leafPrime = leaf.replaceTokens(0, toIndex, TokenArray.EMPTY)
      cursor = next.cursor.replace(owner, leafPrime).asTokenCursor()
      remaining -= toIndex
    }
    val newRope = cursor.cursor.rope(owner)

    this.charCount = newRope.size(TokenMonoid.CharCount)
    this.editCount = newRope.size(TokenMonoid.EditCount)
    this.restartableStateCount = newRope.size(TokenMonoid.RestartableStateCount)
    this.tokenCount = newRope.size(TokenMonoid.TokenCount)
    this.cursor = cursor
  }
}
