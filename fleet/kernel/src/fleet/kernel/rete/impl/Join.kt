// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rete.impl

import fleet.kernel.rete.*

typealias JoinMemory<T, U> = HashMap<T, HashSet<Match<U>>>

fun <L, R, T> joinOn(left: Query<L>, onLeft: (Match<L>) -> T,
                     right: Query<R>, onRight: (Match<R>) -> T): Query<JoinPair<L, R, T>> = run {
  val leftMapped = left.flatMapMatch { setOf(it.value to onLeft(it)) }
  val rightMapped = right.flatMapMatch { setOf(it.value to onRight(it)) }
  Query {
    rawJoinOn(left = leftMapped.producer(),
              onLeft = { it.value.second },
              right = rightMapped.producer(),
              onRight = { it.value.second })
      .rawMap { p ->
        JoinPair(p.value.left.first,
                 p.value.right.first,
                 p.value.on)
      }
  }
}

fun <L, R, T> SubscriptionScope.rawJoinOn(
  left: Producer<L>, onLeft: (Match<L>) -> T,
  right: Producer<R>, onRight: (Match<R>) -> T
): Producer<JoinPair<L, R, T>> = run {
  val broadcast = Broadcaster<JoinPair<L, R, T>>()
  val leftMemory = JoinMemory<T, L>()
  val rightMemory = JoinMemory<T, R>()
  left.collect { leftToken ->
    val key = onLeft(leftToken.match)
    feedJoin(leftToken, leftMemory, rightMemory, key) { lhs, rhs ->
      broadcast(Token(leftToken.added, lhs.combine(rhs, JoinPair(lhs.value, rhs.value, key))))
    }
  }
  right.collect { rightToken ->
    val key = onRight(rightToken.match)
    feedJoin(rightToken, rightMemory, leftMemory, key) { rhs, lhs ->
      broadcast(Token(rightToken.added, lhs.combine(rhs, JoinPair(lhs.value, rhs.value, key))))
    }
  }

  Producer { emit ->
    leftMemory.forEach { (key, leftMatches) ->
      rightMemory[key]?.forEach { rhs ->
        leftMatches.map { lhs ->
          emit(Token(true, lhs.combine(rhs, JoinPair(lhs.value, rhs.value, key))))
        }
      }
    }
    broadcast.collect(emit)
  }
}

private inline fun <U, V, W> feedJoin(token: Token<U>,
                                      thisMemory: JoinMemory<W, U>,
                                      thatMemory: JoinMemory<W, V>,
                                      key: W,
                                      emit: (thisMatch: Match<U>, thatMatch: Match<V>) -> Unit) {
  val input = token.match
  when (token.added) {
    true -> {
      thisMemory.getOrPut(key) { HashSet() }.add(input)
    }
    false -> {
      val set = checkNotNull(thisMemory[key]) { "empty memory for key $key" }
      set.remove(input)
      if (set.isEmpty()) {
        thisMemory.remove(key)
      }
    }
  }
  thatMemory[key]?.forEach { match ->
    emit(input, match)
  }
}
