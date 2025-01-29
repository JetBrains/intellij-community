// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.tokens

import andel.rope.Rope
import andel.tokens.impl.*
import fleet.fastutil.ints.Int2ObjectOpenHashMap
import fleet.util.CancellationToken
import kotlin.jvm.JvmInline

public class Tokens<T> internal constructor(
  internal val rope: Rope<TokenArray>,
  internal val typeMap: TypeMap<T>,
) {

  override fun hashCode(): Int =
    rope.hashCode() + 1

  override fun equals(other: Any?): Boolean =
    other is Tokens<*> && other.rope == rope

  companion object {
    fun <T> fromTokens(tokens: Sequence<Token2<T>>): Tokens<T> {
      val typeMap = TypeMapBuilder<T>(
        typeIdToType = Int2ObjectOpenHashMap(),
        typeToId = HashMap(),
        nextId = 1
      )
      val tokenArrays = tokens.chunked(DESIRED_LEAF_SIZE).map { tokenList ->
        tokenArray(tokenList.size) {
          tokenList.forEach { token ->
            add(
              typeId = typeMap.typeId(token.type),
              length = token.length,
              restartable = token.restartable,
              edited = false
            )
          }
        }
      }.toList()
      return Tokens(
        rope = TokenMonoid.ropeOf(tokenArrays.ifEmpty { listOf(TokenArray.EMPTY) }),
        typeMap = typeMap.build()
      )
    }

    fun <T : Any> fromOldTokens(tokens: Sequence<Token<T>>, emptyType: T?, cancellationToken: CancellationToken): Tokens<T> {
      val typeMap = TypeMapBuilder<T>(
        typeIdToType = Int2ObjectOpenHashMap(),
        typeToId = HashMap(),
        nextId = 1
      )
      val tokenArrays = tokens.chunked(DESIRED_LEAF_SIZE).map { tokenList ->
        cancellationToken.checkCancelled()
        tokenArray(tokenList.size) {
          tokenList.forEach { token ->
            add(
              typeId = typeMap.typeId(token.type),
              length = token.end - token.start,
              restartable = token.state == 0,
              edited = false
            )
          }
        }
      }.toList()
      val rope = TokenMonoid.ropeOf(tokenArrays.ifEmpty {
        listOf(
          if (emptyType == null) {
            TokenArray.EMPTY
          }
          else tokenArray(1) {
            add(typeId = typeMap.typeId(emptyType),
                length = 0,
                restartable = true,
                edited = false)
          })
      })
      return Tokens(
        rope = rope,
        typeMap = typeMap.build()
      )
    }
  }

  val charCount: Int
    get() = rope.size(TokenMonoid.CharCount)

  val tokenCount: Int
    get() = rope.size(TokenMonoid.TokenCount)

  val editCount: Int
    get() = rope.size(TokenMonoid.EditCount)

  val restartableStateCount: Int
    get() = rope.size(TokenMonoid.RestartableStateCount)

  fun view(): TokensView<T> =
    mutable()

  fun mutable(): MutableTokensView<T> {
    return MutableTokensViewImpl(
      cursor = rope.cursor(Any()).asTokenCursor(),
      typeMap = typeMap.builder(),
      charCount = charCount,
      tokenCount = tokenCount,
      editCount = editCount,
      restartableStateCount = restartableStateCount
    )
  }
}

public interface TokensView<T> {

  val charCount: Int

  val tokenCount: Int

  val editCount: Int

  val restartableStateCount: Int

  fun tokenType(index: TokenIndex): T

  fun tokenStart(index: TokenIndex): Int

  fun tokenEnd(index: TokenIndex): Int

  fun isRestartable(index: TokenIndex): Boolean

  fun isEdited(index: TokenIndex): Boolean

  fun restartableStateCountBefore(index: TokenIndex): Int

  fun tokenIndexAtOffset(offset: Int): TokenIndex

  fun tokenIndexAtRestartableStateIndex(restartableStateIndex: Int): TokenIndex

  fun tokenIndexAtEditIndex(editIndex: Int): TokenIndex

  fun tokens(): Tokens<T>
}

public interface MutableTokensView<T> : TokensView<T> {
  /**
   * Replaces tokens by removing tokens between the [from] (inclusive) and [to] (exclusive), and inserting [newToken2s] at the [from] index.
   *
   * ```kotlin
   * val view = ("a", "b", "c")
   * view.replaceTokens(1, 1, listOf("d")) == ("a", "d", "b", "c")
   * view.replaceTokens(1, 2, listOf("d")) == ("a", "d", "c")
   * view.replaceTokens(1, 2, listOf()) == ("a", "c")
   * ```
   */
  fun replaceTokens(from: TokenIndex, to: TokenIndex, newToken2s: List<Token2<T>>)
}

public data class Token2<T>(
  val length: Int,
  val type: T,
  val edited: Boolean,
  val restartable: Boolean,
)

@JvmInline
public value class TokenIndex(val tokenIndex: Int)

public val Int.tokenIndex: TokenIndex
  get() = TokenIndex(this)

public operator fun TokenIndex.plus(i: Int): TokenIndex =
  (tokenIndex + i).tokenIndex

public fun <T> Tokens<T>.mutate(f: (MutableTokensView<T>) -> Unit): Tokens<T> =
  mutable().apply(f).tokens()
