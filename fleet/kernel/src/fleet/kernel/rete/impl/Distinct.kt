// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rete.impl

import fleet.kernel.rete.*
import java.util.concurrent.ConcurrentHashMap

internal fun <T : Any> SubscriptionScope.distinct(producer: Producer<T>): Producer<T> =
  run {
    val broadcast = Broadcaster<T>()
    val memory = HashMap<T, MutableSet<Match<T>>>()
    producer.collect { token ->
      val v = token.match.value
      when (token.added) {
        true -> {
          when (val matches = memory[v]) {
            null -> {
              val ms = ConcurrentHashMap.newKeySet<Match<T>>()
              memory[v] = ms
              ms.add(token.match)
              broadcast(Token(true, distinctMatchValue(v, ms)))
            }
            else -> {
              matches.add(token.match)
            }
          }
        }
        false -> {
          memory[v]?.let { matches ->
            if (matches.remove(token.match)) {
              if (matches.isEmpty()) {
                memory.remove(v)
                broadcast(Token(false, distinctMatchValue(v, emptySet())))
              }
            }
          }
        }
      }
    }
    Producer { emit ->
      memory.forEach { (v, ms) ->
        emit(Token(true, distinctMatchValue(v, ms)))
      }
      broadcast.collect(emit)
    }
  }

private fun <T> distinctMatchValue(v: T, ms: Set<Match<T>>): Match<T> =
  Match.validatable(v) {
    val anyValid = ms.any { match ->
      match.validate() == ValidationResultEnum.Valid
    }
    if (anyValid) ValidationResultEnum.Valid else ValidationResultEnum.Inconclusive
  }
