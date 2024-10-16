// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rete

import com.jetbrains.rhizomedb.*
import fleet.kernel.*
import fleet.tracing.spannedScope
import fleet.util.async.conflateReduce
import fleet.util.async.use
import fleet.util.channels.channels
import fleet.util.logging.logger
import fleet.util.openmap.merge
import fleet.util.singleOrNullOrThrow
import kotlinx.collections.immutable.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consume
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.selects.whileSelect
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext

sealed interface ReteState {
  data class Db(val db: DB) : ReteState
  data class Poison(val poison: Throwable) : ReteState
}

fun ReteState.dbOrThrow(): DB =
  when (this) {
    is ReteState.Db -> db
    is ReteState.Poison -> throw poison
  }

data class Rete internal constructor(
  internal val commands: SendChannel<Command>,
  internal val reteState: StateFlow<ReteState>,
  internal val dbSource: ReteDbSource,
) : CoroutineContext.Element {

  class ObserverId

  internal sealed interface Command {
    data class AddObserver<T>(
      val dbTimestamp: Long,
      val dependencies: Collection<ObservableMatch<*>>,
      val query: Query<T>,
      val tracingKey: QueryTracingKey?,
      val observerId: ObserverId,
      val observer: QueryObserver<T>,
    ) : Command

    data class RemoveObserver(val observerId: ObserverId) : Command
  }

  companion object : CoroutineContext.Key<Rete> {
    val logger = logger<Rete>()
  }

  override val key: CoroutineContext.Key<*> = Rete
}

class ReteEntity(override val eid: EID) : Entity {
  companion object : EntityType<ReteEntity>(ReteEntity::class, ::ReteEntity) {
    internal val ReteAttr = requiredTransient<Rete>("rete")
    internal val TransactorAttr = requiredTransient<Transactor>("kernel", Indexing.UNIQUE)
    fun forKernel(transactor: Transactor): Rete? = entity(TransactorAttr, transactor)?.get(ReteAttr)
  }
}

/**
 * Sets up [Rete] for use inside [body]
 * Runs a coroutine which consumes changes made in db and commands to add or remove [QueryObserver]s
 * */
suspend fun <T> withRete(failWhenPropagationFailed: Boolean = false, body: suspend CoroutineScope.() -> T): T {
  val (commandsSender, commandsReceiver) = channels<Rete.Command>(Channel.UNLIMITED)
  return spannedScope("withRete") {
    val kernel = transactor()
    kernel.subscribe(Channel.UNLIMITED) { db, changes ->
      val lastKnownDb = MutableStateFlow<ReteState>(ReteState.Db(db))
      val result = runCatching {
        coroutineScope {
          launch {
            spannedScope("rete event loop") {
              // todo: implement a proper reconnect, this could still fail because of thread starvation
              changes.consumeAsFlow()
                .conflateReduce { c1, c2 ->
                  Change(dbBefore = c1.dbBefore,
                         dbAfter = c2.dbAfter,
                         novelty = c1.novelty + c2.novelty,
                         meta = c1.meta.merge(c2.meta))
                }
                .produceIn(this)
                .consume {
                  val changesConflated = this
                  val rete = postponedVars(lastKnownDb, ReteNetwork.new(lastKnownDb, failWhenPropagationFailed))
                  whileSelect {
                    commandsReceiver.onReceive { cmd ->
                      rete.command(cmd)
                      true
                    }
                    changesConflated.onReceiveCatching { changeResult ->
                      when {
                        changeResult.isSuccess -> {
                          val change = changeResult.getOrNull()!!
                          rete.propagateChange(change)
                          true
                        }
                        else -> false
                      }
                    }
                  }
                }
            }
          }.use {
            val rete = Rete(commands = commandsSender,
                            reteState = lastKnownDb,
                            dbSource = ReteDbSource(lastKnownDb))
            val reteEntity = change {
              register(ReteEntity)
              ReteEntity.new {
                it[ReteEntity.ReteAttr] = rete
                it[ReteEntity.TransactorAttr] = kernel
              }
            }
            withContext(rete) {
              body()
            }.also {
              change {
                reteEntity.delete()
              }
            }
          }
        }
      }
      lastKnownDb.value = ReteState.Poison(result.exceptionOrNull() ?: RuntimeException("rete is terminated"))
      result.getOrThrow()
    }
  }
}

/**
 * Runs [body] in a presence of [Match].
 * if [Match] is retracted, [body] is cancelled and [WithMatchResult.Failure] returned to the caller
 * runs [body] on [Rete] [fleet.kernel.DbSource]
 * */
suspend fun <T, U> Match<T>.withMatch(body: suspend CoroutineScope.(T) -> U): WithMatchResult<U> = let { self ->
  withObservableMatches((observableSubmatches() as Sequence<ObservableMatch<*>>).toHashSet()) { body(self.value) }
}

/**
 * Ensures [Rete] has processed all changes up until the timestamp of the [targetDb] or,
 * if not given, until the timestamp of the currently bound db.
 * It rarely makes sense to use this, see [Match.withMatch] instead
 * */
suspend fun waitForReteToCatchUp(targetDb: Q, cancellable: Boolean = true) {
  val targetTimestamp = targetDb.timestamp
  withContext(if (cancellable) EmptyCoroutineContext else NonCancellable) {
    requireNotNull(coroutineContext[Rete]) { "Rete is not found on the context" }
      .reteState.first { reteDb -> reteDb.dbOrThrow().timestamp >= targetTimestamp }
  }

  // now that rete has caught up with targetDb, we can go and check if our context matches are still valid
  val invalidation = coroutineContext[ContextMatches]?.matches
    ?.firstOrNull { it.validity.isCompleted }
    ?.let { invalidatedMatch ->
      UnsatisfiedMatchException(CancellationReason("match invalidated by rete", invalidatedMatch.match))
    }

  // bug in kotlin coroutines:
  // the suspend call is in tail position of a function returning Unit,
  // kotlin decides to reuse the continuation,
  // resulting in a db leak if the calling code suspends right after this.

  // since now, suspend call is not in tail position, we're safe.
  when (invalidation) {
    null -> DbContext.threadBound.set(targetDb)
    else -> {
      Rete.logger.trace { "Caught up to rete, but context matches are invalidated: $invalidation" }
      DbContext.threadBound.setPoison(invalidation)
    }
  }
}

/**
 * Represents a set of matches, in which presence the current coroutine is allowed to run.
 * It is populated by [Match.withMatch]
 * */
data class ContextMatches internal constructor(
  internal val matches: PersistentSet<ObservableMatch<*>>,
) : CoroutineContext.Element {
  companion object : CoroutineContext.Key<ContextMatches>

  override val key: CoroutineContext.Key<*> get() = ContextMatches
}

private sealed class ValidationResult {
  data object Valid : ValidationResult()
  data class Invalid(val marker: Match<*>) : ValidationResult()
  data object Inconclusive : ValidationResult()

  fun and(other: () -> ValidationResult): ValidationResult =
    when (this) {
      Valid -> other()
      is Invalid -> this
      is Inconclusive -> this
    }
}

private val ReteSpinChangeInterceptor: ChangeInterceptor =
  ChangeInterceptor("rete") { changeFn, next ->
    val matchInfos = coroutineContext[ContextMatches]?.matches ?: emptyList()
    val rete = coroutineContext.rete
    tailrec suspend fun spin(): Change {
      lateinit var validationVar: ValidationResult
      val change = next {
        val validation = matchInfos.fold<ObservableMatch<*>, ValidationResult>(ValidationResult.Valid) { r, match ->
          r.and {
            val reteTimestamp = rete.reteState.value.dbOrThrow().timestamp
            when {
              match.validity.isCompleted -> ValidationResult.Invalid(match)
              dbBefore.timestamp == reteTimestamp -> ValidationResult.Valid
              else -> when (match.validate()) {
                ValidationResultEnum.Inconclusive -> ValidationResult.Inconclusive
                ValidationResultEnum.Invalid -> ValidationResult.Invalid(match)
                ValidationResultEnum.Valid -> ValidationResult.Valid
                else -> error("unreachable")
              }
            }
          }
        }
        if (validation == ValidationResult.Valid) {
          changeFn()
        }
        validationVar = validation
      }
      return when (val x = validationVar) {
        is ValidationResult.Invalid -> throw UnsatisfiedMatchException(CancellationReason("match invalidated by rete", x.marker))
        is ValidationResult.Inconclusive -> {
          waitForReteToCatchUp(change.dbAfter)
          spin()
        }
        ValidationResult.Valid -> change
      }
    }

    val change = spin()
    /**
     * see `change suspend is atomic case 2` in [fleet.test.frontend.kernel.TransactorTest]
     * */
    waitForReteToCatchUp(change.dbAfter, cancellable = false)
    change
  }

/**
 * asynchronously adds [QueryObserver] to the [Query], returns a handle to asynchronously unsubscribe
 * [observer] should be as fast as possible or it will inhibit general throughput of the rete network
 */
fun <T> Query<T>.observe(
  rete: Rete,
  dbTimestamp: Long,
  contextMatches: ContextMatches?,
  queryTracingKey: QueryTracingKey?,
  observer: QueryObserver<T>,
): DisposableHandle = let { query ->
  val terminalId = Rete.ObserverId()
  check(rete.commands.trySend(Rete.Command.AddObserver(dbTimestamp = dbTimestamp,
                                                       dependencies = contextMatches?.matches ?: emptyList(),
                                                       query = query,
                                                       tracingKey = queryTracingKey,
                                                       observerId = terminalId,
                                                       observer = observer)).isSuccess) { "Rete is dead" }

  DisposableHandle {
    check(rete.commands.trySend(Rete.Command.RemoveObserver(terminalId)).isSuccess) { "Rete is dead" }
  }
}

/**
 * Runs [body] with [Rete] [DbSource]
 * */
internal suspend fun <T> withReteDbSource(body: suspend () -> T): T =
  requireNotNull(coroutineContext[Rete]) { "no rete on context" }.let { rete ->
    if (coroutineContext[DbSource.ContextElement]?.dbSource == rete.dbSource) {
      body()
    }
    else {
      waitForReteToCatchUp(coroutineContext.transactor.dbState.value)
      val dbSourceContextElement = DbSource.ContextElement(rete.dbSource)
      withContext(dbSourceContextElement + ReteSpinChangeInterceptor) {
        body()
      }
    }
  }

internal data class ReteDbSource(val reteState: StateFlow<ReteState>) : DbSource {
  override val flow: Flow<DB>
    get() = reteState.map { it.dbOrThrow() }
  override val latest: DB
    get() = reteState.value.dbOrThrow()

  override val debugName: String get() = "rete $reteState"
}

private inline fun <T> DisposableHandle.useInline(function: () -> T): T =
  try {
    function()
  }
  finally {
    dispose()
  }

/**
 * Launches a new coroutine for every match of [Query].
 * Coroutine is guaranteed to observe only db, "containing" the match.
 * Whenever Rete coroutine observes the match gone, the coroutine is being proactively cancelled.
 * Cancelled coroutines are being joined, before starting a new ones.
 * */
suspend fun <T> Query<T>.launchOnEach(body: suspend CoroutineScope.(T) -> Unit) {
  supervisorScope {
    val jobs = adaptiveMapOf<Match<*>, Job>()
    tokenSetsFlow().collect { tokenSet ->
      tokenSet.retracted.forEach { m ->
        jobs.remove(m)?.cancelAndJoin()
      }
      tokenSet.asserted.forEach { m ->
        jobs.put(m, launch { m.withMatch(body) })?.let { previous ->
          previous.cancel()
          Rete.logger.warn { "launchOnEach replaced existing job for a match, this should not happen. match: $m" }
        }
      }
    }
  }
}

/**
 * Every [TokenSet] produced by this flow corresponds to a single change to the db, if the set of matches has changed for the [Query]
 * */
fun <T> Query<T>.tokenSetsFlow(): Flow<TokenSet<T>> = let { query ->
  flow {
    val (send, receive) = channels<TokenSet<T>>(Channel.UNLIMITED)
    val rete = currentCoroutineContext().rete
    query.observe(rete = rete,
                  dbTimestamp = db().timestamp,
                  queryTracingKey = currentCoroutineContext()[QueryTracingKey],
                  contextMatches = currentCoroutineContext()[ContextMatches]) { initial ->
      // trySends to the send channel may throw cancellation,
      // but it will also cancel the flow itself,
      // which will lead to observer removal from the DisposableHandle
      send.trySend(TokenSet(asserted = initial,
                            retracted = emptySet()))
      OnTokens { tokens ->
        send.trySend(tokens)
      }
    }.useInline {
      receive.consumeEach { ts ->
        // collector might not be actually suspended, and thus,
        // DbSource will not be invoked to update the db on thread local context.
        // this trick partially mitigates this problem, but it does not always work,
        // because the real collector might be actually running on a different thread
        DbContext.threadBound.set(rete.reteState.value.dbOrThrow())
        emit(ts)
      }
    }
  }
}

/**
 * see [tokenSetsFlow]
 * */
fun <T> Query<T>.tokensFlow(): Flow<Token<T>> =
  tokenSetsFlow().flatMapConcat { tokenSet -> tokenSet.asFlow() }

/**
 * Provides a flow of matches.
 * Retraction of a match may be handled by [Match.withMatch] function
 * */
fun <T> Query<T>.matchesFlow(): Flow<Match<T>> =
  tokensFlow().mapNotNull { t -> t.match.takeIf { t.added } }

/**
 * Transforms a [Query] into a [Flow].
 *
 * A collector of the returned [Flow] will not be cancelled when a match is invalidated, unlike [Query.collect].
 *
 * For example, if [T] contains references to entities, it is possible that a collector of the returned [Flow] will observe a
 * more recent state of the database where these entities already were deleted.
 * */
fun <T> Query<T>.asValuesFlow(): Flow<T> =
  matchesFlow().map { it.value }

/**
 * Returns a flow that applies the given [transform] function to each emitted [Match] value under [withMatch]
 * and flattens the resulting flow.
 * When new match arrives, the previous work is cancelled, see [Flow.collectLatest]
 */
fun <T, R> Flow<Match<T>>.flatMapLatestMatch(transform: suspend (value: T) -> Flow<R>): Flow<Match<R>> =
  transformLatest { match ->
    match.withMatch {
      transform(it).collect { r ->
        emit(match.withValue(r))
      }
    }
  }

/**
 * Returns a flow that applies the given [transform] function to each emitted [Match] value under [Match.withMatch]
 * and flattens the resulting flow.
 * When new match arrives, the previous work is cancelled, see [Flow.collectLatest]
 */
fun <T, R> Flow<Match<T>>.flatMapMatchLatestMatch(transform: suspend (value: T) -> Flow<Match<R>>): Flow<Match<R>> =
  transformLatest { outerMatch ->
    outerMatch.withMatch {
      transform(it).collect { match ->
        emit(outerMatch.combine(match) { _, v -> v })
      }
    }
  }

/**
 * Runs collector with each match, under [Match.withMatch]
 * */
suspend fun <T> Flow<Match<T>>.collectMatches(collector: suspend CoroutineScope.(T) -> Unit) {
  collect { match -> match.withMatch(collector) }
}

/**
 * Runs collector with each match, under [Match.withMatch]
 * when new match arrives, the previous work is cancelled, see [Flow.collectLatest]
 * */
suspend fun <T> Flow<Match<T>>.collectLatestMatch(collector: suspend CoroutineScope.(T) -> Unit) {
  collectLatest { match -> match.withMatch(collector) }
}

/**
 * invokes [f] sequentially with each value of [Query] [Match]es
 * [f] is run in a presence of each [Match], cancelled if [Match] is retracted, and will be invoked for the subsequent ones
 * */
suspend fun <T> Query<T>.collect(f: suspend CoroutineScope.(T) -> Unit) {
  matchesFlow().collectMatches(f)
}

/**
 * invokes [f] sequentially with each value of [Query] [Match]es
 * when new match arrives, the previous work is cancelled, see [Flow.collectLatest]
 * */
suspend fun <T> Query<T>.collectLatest(f: suspend CoroutineScope.(T) -> Unit) {
  matchesFlow().collectLatestMatch(f)
}

/**
 * returns first value of [Query] [Match]es
 * */
suspend fun <T> Query<T>.first(): T = matchesFlow().first().value

/**
 * returns first value of [Query] [Match]es that satisfies [p] to true,
 * [p] is being read-tracked
 * */
suspend fun <T> Query<T>.first(p: (T) -> Boolean): T = filter(p).first()

private val CoroutineContext.rete: Rete
  get() = requireNotNull(this[Rete]) { "no Rete on context $this" }

/**
 * Accepts only single-valued queries, runs [body] with which ever value is current, cancels it when it is retracted
 * */
suspend fun <T, U> Query<T>.withCurrentMatch(body: suspend CoroutineScope.(T) -> U): WithMatchResult<U> =
  tokenSetsFlow().map { ts ->
    when (val currentMatch = ts.asserted.singleOrNullOrThrow()) {
      null -> WithMatchResult.Failure(CancellationReason("condition invalidated", null))
      else -> currentMatch.withMatch(body)
    }
  }.first()

/**
 * runs [body], no more than once, while the [PredicateQuery] holds.
 * if match is retracted before [body] succeeds, [body] is cancelled and [WithMatchResult.Failure] returned to a caller
 * */
suspend fun <T> PredicateQuery.withPredicate(body: suspend CoroutineScope.() -> T): WithMatchResult<T> =
  withCurrentMatch { body() }

sealed interface FinalValue<T> {
  data class Value<T>(val value: T) : FinalValue<T>
  class Absent<T> : FinalValue<T>
}

/**
 * Defined for single-valued queries, returns the last asserted value before the query is empty.
 * */
suspend fun <T : Any> Query<T>.finalValue(): FinalValue<T> =
  when (val lastSet = tokenSetsFlow().takeWhile { ts -> ts.asserted.isNotEmpty() }.lastOrNull()) {
    null -> FinalValue.Absent()
    else -> FinalValue.Value(lastSet.asserted.single().value)
  }
