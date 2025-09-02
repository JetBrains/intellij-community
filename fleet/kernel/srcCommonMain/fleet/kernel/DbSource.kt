// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel

import com.jetbrains.rhizomedb.DB
import com.jetbrains.rhizomedb.DbContext
import com.jetbrains.rhizomedb.asOf
import fleet.kernel.rete.CancellationReason
import fleet.kernel.rete.ContextMatches
import fleet.kernel.rete.ReteEntity
import fleet.kernel.rete.UnsatisfiedMatchException
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

interface DbSource {
  val flow: Flow<DB>
  val latest: DB
  val debugName: String

  class ContextElement(val dbSource: DbSource) : ThreadContextElement<DbContext<*>?> {
    override val key: CoroutineContext.Key<*> = ContextElement

    companion object : CoroutineContext.Key<ContextElement>

    override fun updateThreadContext(context: CoroutineContext): DbContext<*>? {
      // resuming
      val oldState = DbContext.threadBoundOrNull
      runCatching { dbSource.latest }
        .onSuccess { latest ->
          val ctx = DbContext<DB>(latest, dbSource)
          DbContext.threadLocal.set(ctx)
          context[ContextMatches]?.matches?.firstOrNull { m -> m.wasInvalidated }?.let { cancelledMatch ->
            ctx.setPoison(UnsatisfiedMatchException(CancellationReason("match invalidated by rete", cancelledMatch)))
          }
        }.onFailure { ex ->
          val ctx = DbContext<DB>(RuntimeException("Failed to obtain latest db snapshot", ex), dbSource)
          DbContext.threadLocal.set(ctx)
        }
      return oldState
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: DbContext<*>?) {
      /*
        the code:
        // withContext starts an undispatched coroutine (will start execution on this very thread).
        // the DbSource.updateThreadContext will be invoked (even though it was no required, see [withContext] impl), binding a new DbContext on thread local
        val e = withContext(CoroutineName("Hello")) {
          // change will bump the DbContext and if it does not really suspend
          change {
            new(TestEntity::class) {
              vector = BifurcanVector()
            }
          }
        }
        // withContext will restore the old DbContext
        // then we continue here without invoking updateThreadContext again, leaving us with the old db.
        assertTrue(e.exists())
      */
      /*
       When leaving thread, we should bump the threadBound DbContext with the db from original DbSource, not the one leaving the thread.
      */
      oldState?.let {
        (oldState.dbSource as DbSource?)?.let { dbSource ->
          runCatching { dbSource.latest }
            .onSuccess { latest -> oldState.set(latest) }
            .onFailure { ex -> oldState.setPoison(RuntimeException("Failed to obtain latest db snapshot", ex)) }
        }
      }
      DbContext.threadLocal.set(oldState)
    }

    override fun toString(): String = "DbSourceContextElement(${dbSource.debugName})"
  }
}

class ConstantDBSource(private val db: DB) : DbSource {
  override val flow: Flow<DB>
    get() = error("This DBSource is constant, the only database it can possibly return is the [latest] one, there is no point in waiting")

  override val latest: DB
    get() = db

  override val debugName: String
    get() = "ConstantDbSource($db)"
}

class FlowDbSource(
  internal val stateFlow: StateFlow<DB>,
  override val debugName: String,
) : DbSource {
  override val flow: Flow<DB>
    get() = stateFlow

  override val latest: DB
    get() = stateFlow.value

  override fun toString(): String =
    "FlowDbSource(${debugName})"
}


fun KernelContextElement(transactor: Transactor, dbSource: DbSource = FlowDbSource(transactor.dbState, "kernel $transactor")): CoroutineContext =
  transactor +
  DbSource.ContextElement(dbSource) +
  (asOf(transactor.dbState.value) { ReteEntity.forKernel(transactor) } ?: EmptyCoroutineContext)

fun ConstantDbContext(db: DB): CoroutineContext =
  DbSource.ContextElement(ConstantDBSource(db))

/**
 * [Transactor] on which the [saga] is started
 * */
val CoroutineContext.transactor: Transactor
  get() = requireNotNull(this[Transactor]) { "no kernel on coroutineContext" }

val CoroutineContext.dbSource: DbSource
  get() = requireNotNull(this[DbSource.ContextElement]) { "no DbSource on coroutineContext" }.dbSource