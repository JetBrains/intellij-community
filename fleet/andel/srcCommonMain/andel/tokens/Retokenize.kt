// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.tokens

import fleet.util.CancellationToken

fun <T : Any> tokenize2(text: CharSequence, lexer: Tokenizer<T>, cancellationToken: CancellationToken, emptyType: T?): Tokens<T> {
  val tokens = lexer.tokenize(text, 0, text.length, 0)
  return Tokens.fromOldTokens(tokens, emptyType, cancellationToken)
}

public fun <T: Any> Tokens<T>.retokenize(
  charSequence: CharSequence, 
  tokenizer: Tokenizer<T>, 
  cancellationToken: CancellationToken,
  emtpyType: T?,
): Tokens<T> {
  val mut = mutable()
  while (mut.editCount > 0) {
    cancellationToken.checkCancelled()
    val editedTokenIndex = mut.tokenIndexAtEditIndex(0)
    val restartableStatesBeforeEdit = mut.restartableStateCountBefore(editedTokenIndex)
    val restartableStateTokenIndex = when {
      restartableStatesBeforeEdit > 0 -> mut.tokenIndexAtRestartableStateIndex(restartableStatesBeforeEdit - 1)
      else -> 0.tokenIndex
    }
    val restartableStateOffset = mut.tokenStart(restartableStateTokenIndex)
    val newTokensIterator = tokenizer
      .tokenize(charSequence, restartableStateOffset, charSequence.length, 0)
      .map {
        Token2(
          length = it.end - it.start,
          type = it.type,
          edited = false,
          restartable = it.state == 0
        )
      }
      .iterator()

    val newTokensList = ArrayList<Token2<T>>()
    val toIndex =
      loop(
        oldTokens = mut,
        out = newTokensList,
        newTokens = newTokensIterator,
        newTokenEnd = restartableStateOffset,
        oldTokenIndex = restartableStateTokenIndex,
        oldTokenEnd = restartableStateOffset,
        oldTokenType = mut.tokenType(restartableStateTokenIndex),
        oldRestartable = false,
        currentEditTokenIndex = editedTokenIndex,
        cancellationToken = cancellationToken
      )

    val toOffset = mut.tokenStart(toIndex)
    val fromOffset = restartableStateOffset
    val newTokensCharCount = newTokensList.sumOf { it.length }
    require(toOffset - fromOffset == newTokensCharCount) {
      "replacement has different length: $newTokensCharCount, from original: ${toOffset - fromOffset}"
    }
    val oldCharCount = mut.charCount
    val oldTokenCount = mut.tokenCount
    mut.replaceTokens(from = restartableStateTokenIndex,
                      to = toIndex,
                      newToken2s = newTokensList)
    require(mut.tokenCount == restartableStateTokenIndex.tokenIndex + newTokensList.size + (oldTokenCount - toIndex.tokenIndex)) {
      "after replacement token count is inconsistent. new tokenCount ${mut.tokenCount}, should have been: ${restartableStateTokenIndex.tokenIndex + newTokensList.size + (oldTokenCount - toIndex.tokenIndex)}"
    }
    require(mut.charCount == oldCharCount) {
      "after replacement charCount should not have been changed newCharCount ${mut.charCount}, oldCharCount $oldCharCount"
    }
  }
  val res = mut.tokens()
  return if (res.tokenCount == 0) Tokens.fromOldTokens(emptySequence(), emtpyType, cancellationToken) else res
}

private tailrec fun <T> loop(
  oldTokens: TokensView<T>,
  out: MutableList<Token2<T>>,
  newTokens: Iterator<Token2<T>>,
  newTokenEnd: Int,
  oldTokenIndex: TokenIndex,
  oldTokenEnd: Int,
  oldTokenType: T,
  oldRestartable: Boolean,
  currentEditTokenIndex: TokenIndex,
  cancellationToken: CancellationToken,
): TokenIndex {
  cancellationToken.checkCancelled()
  return when {
    oldTokenIndex.tokenIndex + 1 < oldTokens.tokenCount && oldTokenEnd <= newTokenEnd -> {
      val oldTokenIndexPrime = (oldTokenIndex.tokenIndex + 1).tokenIndex
      loop(
        oldTokens = oldTokens,
        out = out,
        newTokens = newTokens,
        newTokenEnd = newTokenEnd,

        oldTokenIndex = oldTokenIndexPrime,
        oldTokenEnd = oldTokens.tokenEnd(oldTokenIndexPrime),
        oldTokenType = oldTokens.tokenType(oldTokenIndexPrime),
        oldRestartable = oldTokens.isRestartable(oldTokenIndexPrime),

        currentEditTokenIndex = currentEditTokenIndex,
        cancellationToken = cancellationToken
      )
    }

    newTokens.hasNext() -> {
      val newToken = newTokens.next()
      out.add(newToken)
      val newTokenType = newToken.type
      val newTokenEndPrime = newTokenEnd + newToken.length
      val newRestratable = newToken.restartable
      when {
        (
          currentEditTokenIndex.tokenIndex < oldTokenIndex.tokenIndex &&
//                newTokenStart == oldTokenStart &&
          newTokenEndPrime == oldTokenEnd &&
          newTokenType == oldTokenType &&
          newRestratable && oldRestartable
        ) -> {
          (oldTokenIndex.tokenIndex + 1).tokenIndex
        }
        else -> {
          loop(
            oldTokens = oldTokens,
            out = out,
            newTokens = newTokens,
            newTokenEnd = newTokenEndPrime,
            oldTokenIndex = oldTokenIndex,
            oldTokenEnd = oldTokenEnd,
            oldTokenType = oldTokenType,
            oldRestartable = oldRestartable,
            currentEditTokenIndex = currentEditTokenIndex,
            cancellationToken = cancellationToken
          )
        }
      }
    }
    else -> (oldTokenIndex.tokenIndex + 1).tokenIndex
  }
}
