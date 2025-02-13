// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel

import com.jetbrains.rhizomedb.*
import com.jetbrains.rhizomedb.Entity
import fleet.reporting.shared.tracing.spannedScope
import fleet.util.async.*
import fleet.util.logging.logger
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * Tests put it on top level coroutine context if they do not expect exceptions from the test body.
 *
 * Some exceptions are outside of our control, like IO.
 * Some, like IllegalArgumentException, are not supposed to happen,
 * yet production code will suppress them because they are not necessarily fatal.
 * It makes sense to fail a test that experiences the exception though.
 * If this is the case - check for presence of this element and change you strategy accordingly.
 *
 * For example:
 * - [fleet.kernel.rete.launchOnEach] will run coroutines on un-supervised scope
 *   and re-throw immediately if one of the matches fails.
 * - [fleet.kernel.plugins.PluginScope] will be created with Job instead of SupervisorJob,
 *   which means failure in any worker or action will terminate the application.
 * */
object FailFastMarker : CoroutineContext.Element, CoroutineContext.Key<FailFastMarker> {
  override val key: CoroutineContext.Key<*> get() = this
}

val CoroutineContext.shouldFailFast: Boolean
  get() = this[FailFastMarker] != null

data class SagaScopeEntity(override val eid: EID) : Entity {
  companion object : EntityType<SagaScopeEntity>(SagaScopeEntity::class, ::SagaScopeEntity) {
    val logger = logger<SagaScopeEntity>()
    val SagaScopeAttr = requiredTransient<CoroutineScope>("sagaScope")
    val KernelAttr = requiredTransient<Transactor>("kernel", Indexing.UNIQUE)
  }

  val sagaScope by SagaScopeAttr

  val transactor by KernelAttr
}

suspend fun<T> sagaScope(body: suspend CoroutineScope.(CoroutineScope) -> T): T =
  coroutineScope {
    resource { cc ->
      spannedScope("sagaScope") {
        when {
          coroutineContext[FailFastMarker] != null ->
            coroutineScope {
              cc(this)
            }
          else ->
            supervisorScope {
              cc(this)
            }
        }
      }
    }.async().use { sagaScopeDeferred ->
      val sagaScope = sagaScopeDeferred.await()
      val kernel = transactor()
      val sagaScopeEntity = change {
        register(SagaScopeEntity)
        SagaScopeEntity.new {
          it[SagaScopeEntity.KernelAttr] = kernel
          it[SagaScopeEntity.SagaScopeAttr] = sagaScope
        }
      }
      try {
        coroutineScope { body(sagaScope) }
      }
      finally {
        sagaScope.coroutineContext.job.cancelAndJoin()
        change {
          sagaScopeEntity.delete()
        }
      }
    }
  }
