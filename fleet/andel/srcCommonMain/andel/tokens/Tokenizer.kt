// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package andel.tokens

data class Token<T>(val start: Int,
                    val end: Int,
                    val type: T,
                    /**
                     * 0 if restartable
                     */
                    val state: Int)

fun interface Tokenizer<T> {
  //TODO: consider replacing Sequence with an equivalent thing but without explicit Tokens allocation
  fun tokenize(text: CharSequence, start: Int, end: Int, state: Int): Sequence<Token<T>>
}
