// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.tokens.impl

import andel.util.chunked
import andel.util.replace
import kotlin.jvm.JvmInline

// every token is encoded as two ints [end, [type, restartable, edited]]

@JvmInline
internal value class TokenArray internal constructor(val ints: IntArray) {

  val tokenCount: Int
    get() = ints.size / 2

  val charCount: Int
    get() = if (tokenCount == 0) 0 else end(tokenCount - 1)

  fun typeId(index: Int): Int =
    tokenTag(index).typeId

  fun start(index: Int): Int =
    if (index == 0) 0 else end(index - 1)

  fun end(index: Int): Int =
    ints[2 * index]

  private fun tokenTag(index: Int): TokenTag =
    TokenTag(ints[2 * index + 1])

  fun isRestartable(index: Int): Boolean =
    tokenTag(index).restartable

  fun isEdited(index: Int): Boolean =
    tokenTag(index).edited

  fun indexByOffset(offset: Int): Int {
    var i = 0
    val count = tokenCount
    while (i < count) {
      if (offset < end(i)) {
        return i
      }
      i++
    }
    //    throw NoSuchElementException("offset = $offset, charCount = $charCount")
    return tokenCount - 1
  }

  fun indexAtRestartableStateIndex(restartableStateIndex: Int): Int {
    var i = 0
    var restartableCount = 0
    val tokenCount = tokenCount
    while (i < tokenCount) {
      if (isRestartable(i)) {
        restartableCount++
      }
      if (restartableCount == restartableStateIndex + 1) return i
      i++
    }
    throw NoSuchElementException("restartableStateIndex = $restartableStateIndex")
  }

  fun restartableStateCountBefore(index: Int): Int {
    var i = 0
    var restartableStateCount = 0
    while (i < index) {
      if (isRestartable(i)) {
        restartableStateCount++
      }
      i++
    }
    return restartableStateCount
  }

  fun indexAtEditIndex(editIndex: Int): Int {
    var i = 0
    var editCount = 0
    val tokenCount = tokenCount
    while (i < tokenCount) {
      if (isEdited(i)) {
        editCount++
      }
      if (editCount == editIndex + 1) return i
      i++
    }
    throw NoSuchElementException("editIndex = $editIndex")
  }

  fun measure(): TokenArrayMetric {
    var i = 0
    val count = tokenCount
    var restartableStateCount = 0
    var editCount = 0
    while (i < count) {
      if (isEdited(i)) {
        editCount++
      }
      if (isRestartable(i)) {
        restartableStateCount++
      }
      i++
    }
    return TokenArrayMetric(restartableStateCount, editCount)
  }

  fun concat(rhs: TokenArray): TokenArray =
    when {
      this.tokenCount == 0 -> rhs
      rhs.tokenCount == 0 -> this
      else -> {
        val concatInts = ints + rhs.ints
        var i = ints.size
        val len = charCount
        while (i < concatInts.size) {
          concatInts[i] += len
          i += 2
        }
        TokenArray(concatInts)
      }
    }

  fun split(chunk: Int): List<TokenArray> =
    when {
      tokenCount < chunk -> listOf(this)
      else -> {
        val chunks = ints.chunked(chunk * 2)
        var i = 1
        val firstChunk = chunks[0]
        var offset = firstChunk[firstChunk.size - 2]
        while (i < chunks.size) {
          var j = 0
          val chunk = chunks[i]
          while (j < chunk.size) {
            chunk[j] -= offset
            j += 2
          }
          offset += chunk[chunk.size - 2]
          i++
        }
        chunks.map { TokenArray(it) }
      }
    }

  // every token is encoded as two ints [end, [type, restartable, edited]]

  fun replaceTokens(fromIndex: Int, toIndex: Int, tokens: TokenArray): TokenArray {
    val replaced = ints.replace(fromIndex * 2, toIndex * 2, tokens.ints)
    val fromStart = start(fromIndex)
    val toStart = start(toIndex)
    val toPrime = (fromIndex + tokens.tokenCount)
    val offset1 = start(fromIndex)
    for (i in fromIndex until toPrime) {
      replaced[i * 2] += offset1
    }
    val oldTailStartOffset = start(toIndex)
    val newTailStartOffset = if (toPrime > 0) replaced[(toPrime - 1) * 2] else 0
    val offset2 = newTailStartOffset - oldTailStartOffset
    for (i in toPrime until replaced.size / 2) {
      replaced[i * 2] += offset2
    }
    return TokenArray(replaced).also { res ->
      require(res.charCount == charCount - (toStart - fromStart) + tokens.charCount)
    }
  }

  companion object {
    val EMPTY: TokenArray = TokenArray(intArrayOf())
  }
}

@JvmInline
internal value class TokenTag(internal val int: Int) {
  val typeId: Int
    get() = int shr 2

  val restartable: Boolean
    get() = int and (1 shl 1) != 0

  val edited: Boolean
    get() = (int and 1) != 0
}

internal fun tokenTag(typeId: Int, restartable: Boolean, edited: Boolean): TokenTag =
  TokenTag((typeId shl 2) or ((if (restartable) 1 else 0) shl 1) or (if (edited) 1 else 0))

internal interface TokenArrayBuilder {
  fun add(typeId: Int, length: Int, restartable: Boolean, edited: Boolean)
}

internal fun tokenArray(size: Int, builder: TokenArrayBuilder.() -> Unit): TokenArray {
  val ints = IntArray(size * 2)
  object : TokenArrayBuilder {
    var i = 0
    var end = 0
    override fun add(typeId: Int, length: Int, restartable: Boolean, edited: Boolean) {
      val index = i
      ints[index] = end + length
      ints[index + 1] = tokenTag(typeId, restartable, edited).int
      end += length
      i += 2
    }
  }.builder()
  return TokenArray(ints)
}

internal data class TokenArrayMetric(
  val restartableStateCount: Int,
  val editCount: Int,
)