// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rete.impl

import fleet.kernel.rete.*
import fleet.util.causeOfType
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.*
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.coroutines.coroutineContext

internal class ObservableMatch<T>(
  internal val observerId: NodeId,
  internal val match: Match<T>,
) : Match<T> {

  private val validity: CompletableJob = Job()

  private val invalidated: AtomicBoolean = AtomicBoolean(false)

  override val value: T
    get() = match.value

  /*
   * Rete network has already marked this match as invalidated in one of the snapshots it observed
   */
  val wasInvalidated: Boolean get() = invalidated.load()

  internal fun onInvalidation(handler: () -> Unit): DisposableHandle =
    validity.invokeOnCompletion {
      handler()
    }

  internal fun invalidate() {
    validity.complete()
    invalidated.store(true)
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
  val contextMatches = coroutineContext[ContextMatches]?.matches ?: persistentListOf()

  @Suppress("NAME_SHADOWING")
  val matches = run {
    val set = contextMatches.toSet()
    matches.filter { it !in set }.toList()
  }
  return when {
    matches.isEmpty() -> WithMatchResult.Success(coroutineScope(body))
    else ->
      withReteDbSource {
        var handles: List<DisposableHandle>? = null
        val def = async(context = ContextMatches(contextMatches.addAll(matches)),
                        start = CoroutineStart.UNDISPATCHED) {
          val self = this
          // setup invalidation handles before launching [body]
          // if any of the matches is invalidated, [body] might catch a poison, the job should not be active at this point
          handles = matches.map { m ->
            m.onInvalidation {
              val reason = CancellationReason("match terminated by rete", m)
              self.cancel(UnsatisfiedMatchException(reason))
            }
          }
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
          val cause = ex.causeOfType<UnsatisfiedMatchException>()
          when {
            cause != null && cause.reason.match in matches -> WithMatchResult.Failure(cause.reason)
            else -> throw ex
          }
        }
      }
  }
}

internal fun <T> Query<T>.observable(terminalId: NodeId): Query<T> =
  Query {
    val observableMatches = adaptiveMapOf<Match<T>, ObservableMatch<T>>()
    onDispose {
      // when the terminal is retracted from the network for other reasons, make sure jobs of Matches served by the terminal are cancelled:
      observableMatches.forEach { (_, a) ->
        a.invalidate()
      }
    }
    producer().transform { token, emit ->
      when (token.added) {
        true -> {
          val observableMatch = ObservableMatch(terminalId, token.match)
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
              observableMatch.invalidate()
              emit(Token(false, observableMatch))
            }
          }
        }
      }
    }
  }