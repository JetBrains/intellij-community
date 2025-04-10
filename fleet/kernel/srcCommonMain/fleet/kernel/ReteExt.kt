// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel

import com.jetbrains.rhizomedb.*
import fleet.kernel.rete.*
import fleet.util.logging.logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.concurrent.atomics.AtomicReference

/**
 * [f] will be invoked each time when it's return value may be changed.
 * All subsequent repetitions of the same value are filtered out.
 * @return cold flow of read values, will subscribe to changes only when flow termination operation is invoked.
 *
 *  Note: this function has existed before Rete, which probably means that all it's usages are outdated and perhaps contains consistency issues.
 *  These are highlighted in the editor now, so that responsible developer could think a little bit and do one of two things:
 *  1. just `query {}.matchesFlow().map { it.value }` to lose the consistency explicitly, or
 *  2. `query{}.collect {}`, or `query {}.matchesFlow().transform().collectMatches { }`
 */
@Deprecated("Use query", replaceWith = ReplaceWith("query { f() }", imports = ["fleet.kernel.rete.query"]))
suspend fun <T> queryAsFlow(f: () -> T): Flow<T> =
  query { f() }.asValuesFlow()


suspend fun <T : Entity> launchOnEachEntity(entityType: EntityType<T>, f: suspend CoroutineScope.(T) -> Unit) {
  entityType.each().launchOnEach { v ->
    f(v)
  }
}

private sealed class State11 {
  data object Initial : State11()
  data object ActionInvoked : State11()
  class HandleInitialized(val handle: DisposableHandle) : State11()
}

/**
 * [action] is invoked asynchronously after the entity is deleted
 */
fun Entity.onDispose(rete: Rete, action: () -> Unit = {}): DisposableHandle =
  let { entity ->
    when {
      !exists() -> {
        action()
        DisposableHandle { }
      }
      else -> {
        val state = AtomicReference<State11>(State11.Initial)
        val handle = existence().observe(rete = rete,
                                         contextMatches = null,
                                         queryTracingKey = null,
                                         dbTimestamp = DbContext.threadBound.impl.timestamp + 1) { matches ->
          fun actionPrime() {
            when (val witness = state.compareAndExchange(State11.Initial, State11.ActionInvoked)) {
              is State11.Initial -> {
                action()
              }
              is State11.HandleInitialized -> {
                action()
                witness.handle.dispose()
              }
              else -> error("double retraction?")
            }
          }

          if (matches.isEmpty()) {
            actionPrime()
            OnTokens.noop()
          }
          else {
            OnTokens { tokens ->
              if (tokens.retracted.isNotEmpty()) actionPrime()
            }
          }
        }
        when (state.compareAndExchange(State11.Initial, State11.HandleInitialized(handle))) {
          is State11.Initial -> handle
          is State11.ActionInvoked -> {
            handle.dispose()
            DisposableHandle { }
          }
          else -> error("unreachable")
        }
      }
    }
  }

/**
 * same as [tryWithCondition] but will throw [UnsatisfiedMatchException] if the condition is invalidated
 */
suspend fun <T> withCondition(condition: () -> Boolean, body: suspend CoroutineScope.() -> T): T =
  tryWithCondition(condition, body).getOrThrow()

suspend fun waitFor(p: () -> Boolean) {
  if (p()) return
  val result = queryAsFlow { p() }.firstOrNull { it }
  // query could be terminated before our coroutine, null means we are in shutdown
  if (result == null) {
    throw CancellationException("Query was terminated")
  }
}

private object Logger {
  val logger = logger<Logger>()
}

suspend fun <T> waitForNotNullWithTimeout(timeMillis: Long = 30000L, p: () -> T?): T {
  return waitForNotNullWithTimeoutOrNull(timeMillis, p) ?: run {
    Logger.logger.error(Throwable("Timed out waiting for ${p::class} to return not null, $timeMillis ms"))
    throw CancellationException("$p is null, after ${timeMillis}ms")
  }
}

suspend fun <T> waitForNotNullWithTimeoutOrNull(timeMillis: Long = 30000L, p: () -> T?): T? {
  val value = p()
  if (value != null) return value
  return withTimeoutOrNull(timeMillis) { queryAsFlow { p() }.firstOrNull { it != null } }
}

suspend fun <T> waitForNotNull(p: () -> T?): T {
  return waitForNotNullWithTimeout(Long.MAX_VALUE, p)
}

/**
 * guarantees that [entities] exist in the current db for all operations, including suspend [change]
 * see [withCondition]
 *
 * NOTE that in the [shared] blocks the existence has to be checked manually
 */
suspend fun <T> withEntities(vararg entities: Entity, body: suspend CoroutineScope.() -> T): T =
  tryWithEntities(entities = entities, body).getOrThrow()


suspend fun <T> tryWithEntities(vararg entities: Entity, body: suspend CoroutineScope.() -> T): WithMatchResult<T> =
  when {
    entities.isEmpty() -> coroutineScope { WithMatchResult.Success(body()) }
    else -> entities.map(Entity::existence).reduce(Query<Unit>::and).withPredicate(body)
  }

/**
 * guarantees that [condition] is true in the current db for all operations, including suspend [change]
 * if the condition is invalidated, [body] will be cancelled
 *
 * NOTE that in the [shared] blocks the condition has to be checked manually
 */
suspend fun <T> tryWithCondition(condition: () -> Boolean, body: suspend CoroutineScope.() -> T): WithMatchResult<T> =
  predicateQuery(condition).withPredicate(body)
