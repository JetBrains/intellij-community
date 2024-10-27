// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rete.impl

import com.jetbrains.rhizomedb.DbContext
import com.jetbrains.rhizomedb.Q
import com.jetbrains.rhizomedb.ReadTrackingContext
import com.jetbrains.rhizomedb.withReadTrackingContext
import fleet.kernel.rete.*
import fleet.preferences.FleetFromSourcesPaths
import fleet.preferences.isFleetTestMode
import fleet.util.logging.logger
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.longs.LongSet

private object FlatMap {
  val logger = logger<FlatMap>()
}

private val assertionsEnabled = FleetFromSourcesPaths.isRunningFromSources || isFleetTestMode

internal fun <T, U> SubscriptionScope.flatMap(producer: Producer<T>, f: (Match<T>) -> Set<U>): Producer<U> {
  data class MatchInfo(
    val matches: MutableMap<U, Match<U>>,
    val subscription: Subscription,
  )

  val matchesByInput = adaptiveMapOf<Match<*>, MatchInfo>()
  val broadcast: Broadcaster<U> = Broadcaster()
  producer.collect { token ->
    val input = token.match
    when (token.added) {
      false -> {
        val matchInfo = checkNotNull(matchesByInput.remove(input)) {
          "no matches found for ${input}, nothing to unmatch"
        }
        matchInfo.subscription.close()
        matchInfo.matches.forEach { _, match ->
          broadcast(Token(false, match))
        }
      }
      true -> {
        val (us, patterns) = trackReads { f(input) }
        if (assertionsEnabled) {
          val us2 = f(input)
          val funIsPure = us.size == us2.size && us.all { it in us2 }
          //          check(funIsPure) {
          //            "Function ${f::class} produces different results on the same input, this will lead to bugs in production\n" +
          //            "first invocation: $us, second: $us2"
          //          }
          if (!funIsPure) {
            FlatMap.logger.warn {
              "Function ${f::class} produces different results on the same input, this will lead to bugs in production\n" +
              "first invocation: $us, second: $us2"
            }
          }
        }
        val matches = adaptiveMapOf<U, Match<U>>()
        us.forEach { u ->
          val matchValue = Match.validatable(u, input) {
            validateFlatMapResult(u, f, input)
          }
          matches[u] = matchValue
          broadcast(Token(true, matchValue))
        }
        val sub = scope {
          subscribe(patterns) {
            matchesByInput[input]?.matches?.let { matches ->
              val (newUs, newPatterns) = trackReads { f(input) }
              val oldUs = matches.keys
              val removedUs = oldUs - newUs
              val addedUs = newUs - oldUs
              removedUs.forEach { u ->
                when (val removedMatch = matches.remove(u)) {
                  null -> Rete.logger.warn { "the value $u might have non stable hash code" }
                  else -> broadcast(Token(false, removedMatch))
                }
              }
              addedUs.forEach { u ->
                val match = Match.validatable(u, input) {
                  validateFlatMapResult(u, f, input)
                }
                matches[u] = match
                broadcast(Token(true, match))
              }
              newPatterns
            } ?: LongSet.of()
          }
        }
        matchesByInput[input] = MatchInfo(matches, sub)
      }
    }
  }

  return Producer { emit ->
    matchesByInput.forEach { _, matchInfo ->
      matchInfo.matches.forEach { _, m ->
        emit(Token(true, m))
      }
    }
    broadcast.collect(emit)
  }
}

private fun <T, U> validateFlatMapResult(u: U, f: (Match<T>) -> Set<U>, input: Match<T>): ValidationResultEnum =
  if (u in f(input)) ValidationResultEnum.Valid else ValidationResultEnum.Inconclusive

private fun <T> trackReads(f: () -> T): Pair<T, LongSet> =
  DbContext.threadBound.run {
    val patterns = LongOpenHashSet()
    alter(impl.withReadTrackingContext(ReadTrackingContext { pattern ->
      patterns.add(pattern.hash)
    }) as Q) { f() } to patterns
  }
