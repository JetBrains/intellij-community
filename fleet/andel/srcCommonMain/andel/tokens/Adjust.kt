// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.tokens

import andel.operation.Op
import andel.operation.Operation
import andel.text.IntersectionType
import andel.text.TextRange
import andel.text.intersect
import kotlin.math.min

fun <T> Tokens<T>.adjust(offset: Int, delete: Int, insert: Int, emptyType: T): Tokens<T> {
  val mut = mutable()
  if (delete > 0) {
    mut.shrinkAtOffset(offset, delete)
  }
  if (insert > 0) {
    mut.extendAtOffset(offset, insert, emptyType)
  }
  return mut.tokens()
}

fun <T> Tokens<T>.adjust(operation: Operation, emptyType: T): Tokens<T> {
  var charOffset = 0
  val mut = mutable()
  for (op in operation.ops) {
    when (op) {
      is Op.Retain -> {
        charOffset += op.len.toInt()
      }
      is Op.Replace -> {
        if (op.delete.length > 0) {
          mut.shrinkAtOffset(charOffset, op.delete.length)
        }
        if (op.insert.length > 0) {
          mut.extendAtOffset(charOffset, op.insert.length, emptyType)
        }
        charOffset += op.insert.length
      }
    }
  }
  return mut.tokens()
}

public fun <T> MutableTokensView<T>.extendAtOffset(offset: Int, length: Int, emptyTokenType: T) =
  when {
    tokenCount == 0 -> replaceTokens(0.tokenIndex, 0.tokenIndex, listOf(Token2(length = length, type = emptyTokenType, edited = true, restartable = true)))
    else -> {
      val tokenIndexAtOffset = tokenIndexAtOffset(offset)
      val oldCharCount = charCount
      replaceTokens(
        from = tokenIndexAtOffset,
        to = (tokenIndexAtOffset.tokenIndex + 1).tokenIndex,
        newToken2s = listOf(
          Token2(
            length = tokenEnd(tokenIndexAtOffset) - tokenStart(tokenIndexAtOffset) + length,
            type = tokenType(tokenIndexAtOffset),
            edited = true,
            restartable = isRestartable(tokenIndexAtOffset)
          )
        )
      )
      require(charCount == oldCharCount + length) {
        "extend failed, expected charCount = $oldCharCount + $length, actual = $charCount"
      }
    }
  }

public fun <T> MutableTokensView<T>.shrinkAtOffset(offset: Int, length: Int) {
  val tokenIndexAtOffset = tokenIndexAtOffset(offset)
  val replacements = mutableListOf<Token2<T>>()
  var tokenIndex = tokenIndexAtOffset.tokenIndex
  var cutStart = offset
  var remainingLength = length
  while (0 < remainingLength) {
    val tokenStart = tokenStart(tokenIndex.tokenIndex)
    val tokenEnd = tokenEnd(tokenIndex.tokenIndex)
    val tokenType = tokenType(tokenIndex.tokenIndex)
    val restartable = isRestartable(tokenIndex.tokenIndex)

    val cut = min(tokenEnd - cutStart, remainingLength)
    val oldLen = tokenEnd - tokenStart
    replacements.add(
      Token2(
        length = oldLen - cut,
        type = tokenType,
        edited = true,
        restartable = restartable
      )
    )
    remainingLength -= cut
    cutStart = tokenEnd
    tokenIndex++
  }
  val oldCharCount = charCount
  replaceTokens(tokenIndexAtOffset, tokenIndex.tokenIndex, replacements)
  require(charCount == oldCharCount - length) {
    "shrink failed, expected charCount = $oldCharCount - $length, actual = $charCount"
  }
}

fun <T> bulkAdjust(
  lexemeStores: MutableList<Tokens<T>>,
  textRanges: List<TextRange>,
  edit: Operation,
  emptyType: T,
) {
  var lexemeStoreIndex = 0
  val opIter = edit.ops.iterator()
  var op = if (opIter.hasNext()) opIter.next() else null
  var oldCharOffset = 0L
  var relativeCharOffset = -1
  while (lexemeStoreIndex < lexemeStores.size && op != null) {
    val lexemeTextRange = textRanges[lexemeStoreIndex]
    val (start, end) = lexemeTextRange
    val oldCharOffsetEnd = oldCharOffset + op.lenBefore
    val (intersection, intersectionType) = TextRange(oldCharOffset, oldCharOffsetEnd) intersect  lexemeTextRange
    when (op) {
      is Op.Retain -> {
        if (intersectionType == IntersectionType.After || intersectionType == IntersectionType.Outside) {
          lexemeStoreIndex += 1
          relativeCharOffset = -1
        }
        else {
          oldCharOffset += op.len.toInt()
          if (relativeCharOffset != -1) {
            relativeCharOffset += op.len.toInt()
          }
          op = if (opIter.hasNext()) opIter.next() else null
        }
      }
      is Op.Replace -> {
        if (relativeCharOffset == -1) {
          relativeCharOffset = when (intersectionType) {
            IntersectionType.Inside, IntersectionType.After -> (oldCharOffset - start).toInt()
            IntersectionType.Before, IntersectionType.Outside -> 0
            else -> -1
          }
        }
        when (intersectionType) {
          IntersectionType.Inside -> {
            lexemeStores[lexemeStoreIndex] = lexemeStores[lexemeStoreIndex].adjust(relativeCharOffset, intersection.length.toInt(), op.insert.length, emptyType)
            relativeCharOffset += op.insert.length
            oldCharOffset += op.delete.length
            op = if (opIter.hasNext()) opIter.next() else null
          }
          IntersectionType.Before -> {
            lexemeStores[lexemeStoreIndex] = lexemeStores[lexemeStoreIndex].adjust(relativeCharOffset, intersection.length.toInt(), 0, emptyType)
            oldCharOffset += op.delete.length
            op = if (opIter.hasNext()) opIter.next() else null
          }
          IntersectionType.After -> {
            lexemeStores[lexemeStoreIndex] = lexemeStores[lexemeStoreIndex].adjust(relativeCharOffset, intersection.length.toInt(), op.insert.length, emptyType)
            lexemeStoreIndex++
            relativeCharOffset = -1
          }
          IntersectionType.Outside -> {
            lexemeStores[lexemeStoreIndex] = lexemeStores[lexemeStoreIndex].adjust(relativeCharOffset, intersection.length.toInt(), 0, emptyType)
            lexemeStoreIndex++
            relativeCharOffset = -1
          }
          IntersectionType.None -> {
            if (oldCharOffset > end) {
              lexemeStoreIndex++
              relativeCharOffset = -1
            }
            else {
              // oldCharOffset + s.length < start
              oldCharOffset += op.delete.length
              op = if (opIter.hasNext()) opIter.next() else null
            }
          }
        }
      }
    }
  }
}