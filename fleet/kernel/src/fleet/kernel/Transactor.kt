// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel

import com.jetbrains.rhizomedb.*
import com.jetbrains.rhizomedb.impl.*
import fleet.kernel.rebase.OfferContributorEntity
import fleet.kernel.rebase.RemoteKernelConnectionEntity
import fleet.kernel.rebase.WorkspaceClockEntity
import fleet.rpc.client.RpcClientDisconnectedException
import fleet.tracing.runtime.Span
import fleet.tracing.runtime.currentSpan
import fleet.tracing.span
import fleet.tracing.spannedScope
import fleet.util.*
import fleet.util.async.catching
import fleet.util.async.coroutineNameAppended
import fleet.util.async.use
import fleet.util.channels.channels
import fleet.util.channels.consumeEach
import fleet.util.logging.KLogger
import fleet.util.logging.KLoggers
import fleet.util.openmap.Key
import fleet.util.openmap.MutableOpenMap
import fleet.util.openmap.OpenMap
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.builtins.serializer
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTimedValue

/**
 * Carries out db state management
 * Holds a reference to a current DB. Applies changes to the current version of db serially and broadcasts them to subscribers
 * Subscriber is guaranteed to receive changes dispatched after the subscription in order they have happened
 */
interface Transactor : CoroutineContext.Element {

  override val key: CoroutineContext.Key<*> get() = Transactor

  val middleware: TransactorMiddleware

  /**
   * Current db value
   * Returns snapshot of last known db
   *
   * Be aware that [] and [ChangeScope] already carry db with them so this property may give you a db version that is different from context one
   */
  val dbState: StateFlow<DB>

  /**
   * Issues the change
   * [f] will be applied asynchronously and exactly once to the last known db sequentially
   * The result of change will become a next current db value
   * The resulting change is returned as Deferred value
   * Two changes are guaranteed to happen in the same order as they are issued
   * [changeAsync] has highest priority and is reserved for actions that require immediate result, e.g. UI updates. prefer [changeSuspend] if possible
   * Throws [DispatchChannelOverflowException] if dispatchChannel is overflown
   */
  fun changeAsync(f: ChangeScope.() -> Unit): Deferred<Change>

  /**
   * suspend version of [Transactor.changeAsync]
   * returns the resulting [Change]
   * */
  suspend fun changeSuspend(f: ChangeScope.() -> Unit): Change

  val log: Flow<SubscriptionEvent>

  /**
   * Arbitrary meta data associated with [Transactor]
   */
  @Deprecated("will be removed")
  val meta: MutableOpenMap<Transactor>

  companion object : CoroutineContext.Key<Transactor> {
    val logger: KLogger = KLoggers.logger(Transactor::class)
  }
}

/**
 * "Synchronous" version of [Transactor.changeAsync] carried out in [saga]
 * resulting [Change.dbAfter] will be bound to [coroutineContext] after the function returns
 * @return the result of [f]
 * */
suspend fun <T> change(f: ChangeScope.() -> T): T {
  val context = currentCoroutineContext()
  val kernel = context.transactor
  val interceptor = context[ChangeInterceptor] ?: ChangeInterceptor.Identity
  var res: T? = null
  // TODO: check required condition after the change too?
  val change = interceptor.change({ res = f() }) { changeFn ->
    kernel.changeSuspend(changeFn)
  }
  val result = res as T
  DbContext.threadBound.set(change.dbAfter)
  return result
}


suspend fun db(): DB =
  DbContext.threadBound.impl as DB

suspend fun transactor(): Transactor {
  return currentCoroutineContext().transactor
}

/**
 * Subscribes to all changes applied to this Kernel.
 *
 * If the underlying buffer of [ChangesBufferSize] is overflown, the channel will get closed with an exception.
 * Changes performed by subscriber are guaranteed to be seen in the channel.
 * The provided channel is already managed, no need to consume it.
 * This is an error to consume the channel out of scope of Subscriber fn.
 *
 */
@Deprecated("use Kernel.log")
suspend fun <T> Transactor.subscribe(capacity: Int = Channel.RENDEZVOUS, body: Subscriber<T>): T =
  coroutineScope {
    val (send, receive) = channels<Change>(capacity)
    // trick: use channel in place of deferred, cause the latter one would hold the firstDB for the lifetime of the entire subscription
    val firstDB = Channel<DB>(1)
    val job = launch(start = CoroutineStart.UNDISPATCHED,
                     context = Dispatchers.Unconfined) {
      log.collect { e ->
        when (e) {
          is SubscriptionEvent.First -> {
            firstDB.send(e.db)
          }
          is SubscriptionEvent.Next -> {
            send.send(e.change)
          }
          is SubscriptionEvent.Reset -> {
            send.close(RpcClientDisconnectedException("Consumer is too slow or buffer is too small", null))
          }
        }
      }
    }

    try {
      coroutineScope {
        body.run { subscribed(firstDB.receive(), receive) }
      }
    }
    finally {
      withContext(NonCancellable) {
        job.cancelAndJoin()
      }
      val e = RuntimeException("subscription terminated, is subscription being consumed out of scope?")
      firstDB.close(e)
      send.close(e)
    }
  }

fun interface Subscriber<T> {
  /**
   * Changes in the [changes] channel are guaranteed to be sequential, starting from the change applied to [initial].
   * */
  suspend fun CoroutineScope.subscribed(initial: DB, changes: ReceiveChannel<Change>): T
}

val Transactor.lastKnownDb: DB get() = dbState.value

object OnCompleteKey : ChangeScopeKey<MutableList<(Transactor) -> Unit>>
object DeferredChangeKey : ChangeScopeKey<Deferred<Change>>

//todo: add feature to store many spans here
object SpanChangeKey : ChangeScopeKey<Span>

/**
 * the function [f] will be invoked with [Change.dbAfter] when the current change is completed
 **/
fun ChangeScope.onComplete(f: (Transactor) -> Unit) {
  meta.getOrInit(OnCompleteKey) { ArrayList() }.add(f)
}

private data class ChangeTask(
  val f: ChangeScope.() -> Unit,
  val rendezvous: Deferred<Unit>,
  val resultDeferred: CompletableDeferred<Change>,
  val causeSpan: Span?,
)

/**
 * Key for data associated with [Transactor]
 */
interface KernelMetaKey<V : Any> : Key<V, Transactor>

private const val ChangesBufferSize: Int = 1000
private const val DispatchBufferSize: Int = 1000

const val SharedPart: Part = 2 // replicated between all the clients and workspace using RemoteKernel interface
const val FrontendPart: Part = 3 // frontend
const val WorkspacePart: Part = 4 // workspace
const val CommonPart: Part = 1 // shared between frontend and workspace kernel views when running in short-circuited mode

class DispatchChannelOverflowException : RuntimeException("dispatch channel is overflown")

class ChangeInterceptor(
  val debugName: String,
  val change: suspend (changeFn: ChangeScope.() -> Unit, next: suspend (ChangeScope.() -> Unit) -> Change) -> Change,
) : CoroutineContext.Element {
  companion object : CoroutineContext.Key<ChangeInterceptor> {
    val Identity: ChangeInterceptor = ChangeInterceptor("identity") { changeFn, next ->
      next(changeFn)
    }
  }

  override val key: CoroutineContext.Key<*> = ChangeInterceptor

  override fun toString(): String = debugName
}

private sealed interface TransactorEvent {
  data class SequentialChange(
    val timestamp: Long,
    val change: Change,
  ) : TransactorEvent

  data class Init(
    val timestamp: Long,
    val db: DB,
  ) : TransactorEvent

  data class TheEnd(val reason: Throwable?) : TransactorEvent
}

private fun TransactorEvent.db(): DB =
  when (this) {
    is TransactorEvent.Init -> db
    is TransactorEvent.SequentialChange -> change.dbAfter
    is TransactorEvent.TheEnd -> DB.empty()
  }

sealed interface SubscriptionEvent {
  val db: DB

  data class First(override val db: DB) : SubscriptionEvent

  data class Next(val change: Change) : SubscriptionEvent {
    override val db: DB
      get() = change.dbAfter
  }

  data class Reset(override val db: DB) : SubscriptionEvent
}

/**
 * Initialized newly created Kernel with initialDB
 * [Transactor] is stopped when the [body] returns, [Transactor] will not accept changes after termination
 * [middleware] is applied to every change fn synchronously, being able to supply meta to the change, or alter the behavior of fn in other ways
 * Consider adding KernelMiddleware if additional routine has to be performed on every [Transactor.changeAsync]
 */
suspend fun <T> withTransactor(
  entityClasses: List<EntityTypeDefinition>,
  middleware: TransactorMiddleware = TransactorMiddleware.Identity,
  defaultPart: Int = CommonPart,
  body: suspend CoroutineScope.(Transactor) -> T,
): T =
  spannedScope("withKernel") {
    val kernelId: UID = UID.random()
    val initialDb = span("emptyDB") { DB.empty() }
      .change(defaultPart = defaultPart) {
        span("load kernel module") {
          middleware.run {
            performChange {
              withDefaultPart(CommonPart) {
                register(DbTimestamp)
                DbTimestamp.new {
                  it[DbTimestamp.Timestamp] = 0
                }
              }
              // repeat some code from loadPluginLayer
              context.run {
                registerMixin(Durable)
                registerRectractionRelations()
                register(SagaScopeEntity)
                register(OfferContributorEntity)
                register(RemoteKernelConnectionEntity)
                register(WorkspaceClockEntity)
                entityClasses.map { def -> def to addEntityClass(def) }.forEach { (def, entityTypeEID) ->
                  if (def.kClass.isShared()) {
                    initAttributes(entityTypeEID)
                  }
                }
              }
            }
          }
        }
      }.dbAfter

    val priorityDispatchChannel: Channel<ChangeTask> = Channel(capacity = DispatchBufferSize,
                                                               onUndeliveredElement = { it.resultDeferred.cancel() })
    val backgroundDispatchChannel: Channel<ChangeTask> = Channel(capacity = 32,
                                                                 onUndeliveredElement = { it.resultDeferred.cancel() })
    val sharedFlow = MutableSharedFlow<TransactorEvent>(replay = 1,
                                                        extraBufferCapacity = ChangesBufferSize,
                                                        onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val changesThread = Executors.newSingleThreadExecutor { runnable ->
      Thread(runnable, "Kernel event loop thread ${kernelId}").apply {
        isDaemon = true
        priority = Thread.MAX_PRIORITY
      }
    }

    val dbState = object : StateFlow<DB> {
      override val replayCache: List<DB>
        get() = sharedFlow.replayCache.map { it.db() }

      override val value: DB
        get() = sharedFlow.replayCache.let { replayCache ->
          require(replayCache.size == 1) {
            "replayCache size=${replayCache.size}"
          }
          replayCache[0].db()
        }

      override suspend fun collect(collector: FlowCollector<DB>): Nothing {
        sharedFlow.collect { collector.emit(it.db()) }
      }
    }

    val transactor = object : Transactor {
      override val middleware: TransactorMiddleware get() = middleware

      override val meta: MutableOpenMap<Transactor> = OpenMap<Transactor>().mutable()

      override val dbState: StateFlow<DB>
        get() = dbState

      override fun changeAsync(f: ChangeScope.() -> Unit): Deferred<Change> {
        val deferred = CompletableDeferred<Change>()
        val result = priorityDispatchChannel.trySend(ChangeTask(f = f,
                                                                rendezvous = CompletableDeferred(Unit),
                                                                resultDeferred = deferred,
                                                                causeSpan = currentSpan))
        if (!result.isSuccess) {
          if (result.isClosed) {
            deferred.cancel()
          }
          else {
            throw DispatchChannelOverflowException()
          }
        }
        return deferred
      }

      override suspend fun changeSuspend(f: ChangeScope.() -> Unit): Change {
        currentCoroutineContext().job.ensureActive()
        /**
         * DO NOT WRAP THIS BLOCK IN A SCOPE!
         * see `change suspend is atomic case 2` in [fleet.test.frontend.kernel.TransactorTest]
         * */
        return run {
          val rendezvous = CompletableDeferred<Unit>()
          try {
            val deferred = CompletableDeferred<Change>()
            backgroundDispatchChannel.send(ChangeTask(f = f,
                                                      rendezvous = rendezvous,
                                                      resultDeferred = deferred,
                                                      causeSpan = currentSpan))
            /** we want to preserve structured concurrency which means current job should be completed only when [body] has finished
             * see `change suspend is atomic` in [fleet.test.frontend.kernel.TransactorTest]
             */
            withContext(NonCancellable) {
              rendezvous.complete(Unit)
              deferred.await()
            }
          }
          finally {
            rendezvous.completeExceptionally(CancellationException("Suspending change is cancelled"))
          }
        }
      }

      override val log = flow {
        sharedFlow.fold(null as Long?) { prevTs, event ->
          when (event) {
            is TransactorEvent.Init -> {
              emit(SubscriptionEvent.First(event.db))
              event.timestamp
            }
            is TransactorEvent.SequentialChange -> {
              val e = when {
                prevTs == null -> {
                  SubscriptionEvent.First(event.change.dbAfter)
                }
                prevTs + 1 == event.timestamp -> {
                  SubscriptionEvent.Next(event.change)
                }
                else -> {
                  SubscriptionEvent.Reset(event.change.dbAfter)
                }
              }
              emit(e)
              event.timestamp
            }
            is TransactorEvent.TheEnd -> {
              currentCoroutineContext().ensureActive()
              throw CancellationException("Transactor is terminated", event.reason)
            }
          }
        }
      }

      override fun toString(): String {
        return "Kernel($kernelId)"
      }
    }

    sharedFlow.emit(TransactorEvent.Init(timestamp = 0L, db = initialDb))

    launch(coroutineNameAppended("Changes processing job for $transactor") + changesThread.asCoroutineDispatcher(),
           start = CoroutineStart.ATOMIC) {
      spannedScope("kernel changes") {
        var ts = 1L
        consumeEach(priorityDispatchChannel, backgroundDispatchChannel) { changeTask ->
          val changeResult = runCatching {
            // cancellation exception thrown from here means that the coroutiune issued the change is cancelled
            // we should not rethrow it here as it will destroy kernel's event loop
            // in a sense the cancellation is a rogue one, we should treat it as a simple change failure, and thus keep it INSIDE runCatching
            changeTask.rendezvous.await()
            measureTimedValue {
              val dbBefore = dbState.value
              frequentSpan("change", {
                set("ts", (dbBefore.timestamp + 1).toString())
                cause = changeTask.causeSpan
              }) {
                dbBefore.change(defaultPart) {
                  meta[DeferredChangeKey] = changeTask.resultDeferred
                  meta[SpanChangeKey] = currentSpan
                  middleware.run { performChange(changeTask.f) }
                  DbTimestamp.single()[DbTimestamp.Timestamp]++
                }
              }
            }
          }

          changeResult
            .onSuccess { timedChange ->
              val slowReporter = transactor.meta[SlowChangeReporterKernelKey]
              checkDuration(coroutineContext = currentCoroutineContext(),
                            slowReporter = slowReporter,
                            duration = timedChange.duration,
                            location = changeTask.causeSpan)
              val change = timedChange.value
              Transactor.logger.trace { "[$transactor] broadcasting change $change" }
              sharedFlow.emit(TransactorEvent.SequentialChange(timestamp = ts++,
                                                               change = change))
              change.meta[OnCompleteKey]?.forEach { onComplete ->
                catching {
                  asOf(change.dbAfter) {
                    onComplete(transactor)
                  }
                }.onFailure { e ->
                  Transactor.logger.error(e) { "ChangeScope.onComplete action failed" }
                }
              }
              changeTask.resultDeferred.complete(change)
            }
            .onFailure { x ->
              changeTask.resultDeferred.completeExceptionally(x)
              if (x !is CancellationException) {
                Transactor.logger.error(x) {
                  "$transactor change has failed"
                }
              }
            }
        }
      }
    }.apply {
      invokeOnCompletion { x ->
        check(sharedFlow.tryEmit(TransactorEvent.TheEnd(x))) {
          "changeFlow should have been created with drop-oldest"
        }
      }
    }.use {
      try {
        withContext(transactor + DbSource.ContextElement(FlowDbSource(transactor.dbState, debugName = "kernel $transactor")) + coroutineNameAppended("withKernel")) {
          body(transactor)
        }
      }
      finally {
        Transactor.logger.info { "shutting down kernel $transactor" }
        priorityDispatchChannel.close(); backgroundDispatchChannel.close()
        changesThread.shutdown()
      }
    }
  }

private data class DbTimestamp(override val eid: EID) : Entity {
  companion object : EntityType<DbTimestamp>(DbTimestamp::class, ::DbTimestamp) {
    val Timestamp = requiredValue("timestamp", Long.serializer())
  }
}

val Q.timestamp: Long
  get() = asOf(this) {
    DbTimestamp.single()[DbTimestamp.Timestamp]
  }

private fun checkDuration(
  coroutineContext: CoroutineContext,
  slowReporter: SlowChangeReporter?,
  duration: Duration,
  location: Span?,
) {
  val warningThreshold = 50.milliseconds
  val errorThreshold = 200.milliseconds
  when {
    duration <= warningThreshold -> Unit // ok
    duration <= errorThreshold -> {
      Transactor.logger.info { "Duration: $duration, change from: $location" }
      slowReporter?.invoke(coroutineContext, duration.inWholeMilliseconds, location.toString())
    }
    else -> {
      Transactor.logger.info { "Very long change!!! Duration: $duration, change from: $location" }
      slowReporter?.invoke(coroutineContext, duration.inWholeMilliseconds, location.toString())
    }
  }
}

@DslMarker
annotation class KernelDSL

typealias SlowChangeReporter = (CoroutineContext, Long, String) -> Unit

object SlowChangeReporterKernelKey : KernelMetaKey<SlowChangeReporter>
