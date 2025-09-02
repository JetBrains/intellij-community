// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rete.impl

import fleet.kernel.rete.Match
import fleet.kernel.rete.Token
import fleet.kernel.rete.TokenSet

internal class MutableTokenSet : Iterable<Token<*>> {
  val asserted: HashSet<Match<*>> = HashSet()
  val retracted: HashSet<Match<*>> = HashSet()

  fun addAll(tokens: Iterable<Token<*>>) {
    tokens.forEach(this::add)
  }

  fun add(token: Token<*>) {
    when (token.added) {
      true ->
        if (!retracted.remove(token.match)) {
          asserted.add(token.match)
        }
      false ->
        if (!asserted.remove(token.match)) {
          retracted.add(token.match)
        }
    }
  }

  override fun iterator(): Iterator<Token<*>> =
    iterator {
      retracted.forEach { m ->
        yield(Token(false, m))
      }
      asserted.forEach { m ->
        yield(Token(true, m))
      }
    }

  fun <T> asTokenSet(): TokenSet<T> = TokenSet(asserted as Set<Match<T>>, retracted as Set<Match<T>>)
}
