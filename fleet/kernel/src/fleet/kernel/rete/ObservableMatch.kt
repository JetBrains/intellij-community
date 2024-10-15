// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rete

import fleet.util.causeOfType
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select
import kotlin.coroutines.coroutineContext

internal class ObservableMatch<T>(
  internal val observerId: NodeId,
  internal val match: Match<T>,
  internal val invalidation: CompletableJob,
) : Match<T> {
  override val value: T
    get() = match.value

  override fun validate(): ValidationResultEnum =
    match.validate()

  override fun observableSubmatches(): Sequence<Match<*>> =
    sequenceOf(this)

  override fun toString(): String = "($invalidation $observerId $match)"
}

internal suspend fun <U> withObservableMatches(
  matches: Set<ObservableMatch<*>>,
  body: suspend CoroutineScope.() -> U,
): WithMatchResult<U> =
  try {
    val contextMatches = coroutineContext[ContextMatches]?.matches ?: persistentHashSetOf()

    @Suppress("NAME_SHADOWING")
    val matches = matches.filter { it !in contextMatches }
    when {
      matches.isEmpty() -> coroutineScope { WithMatchResult.Success(body()) }
      else ->
        withReteDbSource {
          withContext(ContextMatches(contextMatches.addAll(matches))) {
            val def = async(start = CoroutineStart.UNDISPATCHED) {
              val inactiveMatch = matches.firstOrNull { !it.invalidation.isActive }
              when {
                inactiveMatch == null -> WithMatchResult.Success(body())
                else -> WithMatchResult.Failure(CancellationReason("match terminated by rete", inactiveMatch))
              }
            }
            select {
              def.onAwait { res -> res }
              for (m in matches) {
                m.invalidation.onJoin {
                  val reason = CancellationReason("match terminated by rete", m)
                  def.cancel(UnsatisfiedMatchException(reason))
                  WithMatchResult.Failure(reason)
                }
              }
            }
          }
        }
    }
  }
  catch (ex: CancellationException) {
    val cause = ex.causeOfType<UnsatisfiedMatchException>()
    when {
      cause != null && cause.reason.match in matches -> WithMatchResult.Failure(cause.reason)
      else -> throw ex
    }
  }

internal fun <T> Query<T>.observable(terminalId: NodeId): Query<T> =
  Query {
    val observableMatches = adaptiveMapOf<Match<T>, ObservableMatch<T>>()
    onDispose {
      // when the terminal is retracted from the network for other reasons, make sure jobs of Matches served by the terminal are cancelled:
      if (observableMatches.isNotEmpty()) {
        val ex = RuntimeException("the match is no longer being tracked")
        // use java forEach, entryset is not implemented for AdaptiveMap
        @Suppress("JavaMapForEach")
        observableMatches.forEach { _, a ->
          a.invalidation.completeExceptionally(ex)
        }
      }
    }
    producer().transform { token, emit ->
      when (token.added) {
        true -> {
          val observableMatch = ObservableMatch(terminalId, token.match, Job())
          observableMatches[token.match] = observableMatch
          emit(Token(true, observableMatch))
        }
        false -> {
          when (val observableMatch = observableMatches.remove(token.match)) {
            null -> {
              Rete.logger.warn {
                "rete retracts match that we have never had ${token.match}, might be a problem with value semantics"
              }
            }
            else -> {
              observableMatch.invalidation.complete()
              emit(Token(false, observableMatch))
            }
          }
        }
      }
    }
  }
