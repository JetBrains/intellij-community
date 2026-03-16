// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rete.impl

import fleet.kernel.rete.CancellationReason
import fleet.kernel.rete.ContextMatches
import fleet.kernel.rete.Many
import fleet.kernel.rete.Match
import fleet.kernel.rete.Query
import fleet.kernel.rete.Rete
import fleet.kernel.rete.SubscriptionDisposedException
import fleet.kernel.rete.Token
import fleet.kernel.rete.UnsatisfiedMatchException
import fleet.kernel.rete.ValidationResultEnum
import fleet.kernel.rete.WithMatchResult
import fleet.kernel.rete.transform
import fleet.kernel.rete.withReteDbSource
import fleet.util.causeOfType
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.concurrent.atomics.AtomicInt

enum class InvalidationReason {
  Unsatisfied,
  Disposed
}

private object MatchState {
  const val ACTIVE = 0
  const val BEFORE_UNSTATISFIED = 1
  const val BEFORE_DISPOSED = 2
  const val UNSATISFIED = 3
  const val DISPOSED = 4
}

class ObservableMatch<T> internal constructor(
  internal val observerId: NodeId,
  internal val owner: Any,
  internal val match: Match<T>,
) : Match<T> {

  private val validity: CompletableJob = Job()

  private val state: AtomicInt = AtomicInt(MatchState.ACTIVE)

  override val value: T
    get() = match.value

  /*
   * Rete network has already marked this match as invalidated in one of the snapshots it observed
   */
  val wasInvalidated: Boolean get() = state.load().let { it == MatchState.UNSATISFIED || it == MatchState.DISPOSED }

  internal fun beforeInvalidation(handler: (InvalidationReason) -> Unit): DisposableHandle =
    validity.invokeOnCompletion {
      val reason = when (val state = state.load()) {
        MatchState.BEFORE_DISPOSED, MatchState.DISPOSED -> InvalidationReason.Disposed
        MatchState.BEFORE_UNSTATISFIED, MatchState.UNSATISFIED -> InvalidationReason.Unsatisfied
        else -> error("unexpected state $state")
      }
      handler(reason)
    }

  internal fun invalidate(reason: InvalidationReason) {
    val beforeState = when (reason) {
      InvalidationReason.Unsatisfied -> MatchState.BEFORE_UNSTATISFIED
      InvalidationReason.Disposed -> MatchState.BEFORE_DISPOSED
    }
    state.store(beforeState)
    validity.complete()
    val afterState = when (reason) {
      InvalidationReason.Unsatisfied -> MatchState.UNSATISFIED
      InvalidationReason.Disposed -> MatchState.DISPOSED
    }
    state.store(afterState)
  }

  override fun validate(): ValidationResultEnum =
    match.validate()

  override fun observableSubmatches(): Sequence<Match<*>> =
    sequenceOf(this)

  override fun toString(): String = "($validity $observerId $match)"
}

internal suspend fun <U> withObservableMatches(
  matches: Sequence<ObservableMatch<*>>,
  body: suspend CoroutineScope.() -> U,
): WithMatchResult<U> {
  val contextMatches = currentCoroutineContext()[ContextMatches]?.matches ?: persistentListOf()

  @Suppress("NAME_SHADOWING")
  val addedMatches = run {
    val set = contextMatches.toSet()
    matches.filter { it !in set }.toList()
  }
  return when {
    addedMatches.isEmpty() -> WithMatchResult.Success(coroutineScope(body))
    else ->
      withReteDbSource {
        var handles: List<DisposableHandle>? = null
        val def = async(context = ContextMatches(contextMatches.addAll(addedMatches)),
                        start = CoroutineStart.UNDISPATCHED) {
          val self = this
          // setup invalidation handles before launching [body]
          // if any of the matches is invalidated, [body] might catch a poison, the job should not be active at this point
          handles = addedMatches.map { m ->
            m.beforeInvalidation { reason ->
              val ex = when (reason) {
                InvalidationReason.Unsatisfied -> UnsatisfiedMatchException(CancellationReason("match terminated by rete", m))
                InvalidationReason.Disposed -> SubscriptionDisposedException(m)
              }
              self.cancel(ex)
            }
          }
          // in case beforeInvalidation synchronously cancelled [self]
          currentCoroutineContext().ensureActive()
          WithMatchResult.Success(body())
        }.apply {
          invokeOnCompletion {
            handles!!.forEach { it.dispose() }
          }
        }
        try {
          def.await()
        }
        catch (ex: CancellationException) {
          val unsatisfied = ex.causeOfType<UnsatisfiedMatchException>()
          val disposed = ex.causeOfType<SubscriptionDisposedException>()
          when {
            disposed != null && disposed.m in addedMatches -> throw disposed
            unsatisfied != null && unsatisfied.reason.match in addedMatches -> WithMatchResult.Failure(unsatisfied.reason)
            else -> throw ex
          }
        }
      }
  }
}

internal fun <T> Query<*, T>.observable(owner: Any, terminalId: NodeId): Query<*, T> =
  Query<Many, T> {
    val observableMatches = adaptiveMapOf<Match<T>, ObservableMatch<T>>()
    onDispose {
      // when the terminal is retracted from the network for other reasons, make sure jobs of Matches served by the terminal are cancelled:
      observableMatches.forEach { (_, a) ->
        a.invalidate(InvalidationReason.Disposed)
      }
    }
    producer().transform { token, emit ->
      when (token.added) {
        true -> {
          val observableMatch = ObservableMatch(terminalId, owner, token.match)
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
              observableMatch.invalidate(InvalidationReason.Unsatisfied)
              emit(Token(false, observableMatch))
            }
          }
        }
      }
    }
  }