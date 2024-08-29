// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel

import com.jetbrains.rhizomedb.*
import com.jetbrains.rhizomedb.Entity
import fleet.tracing.spannedScope
import fleet.util.async.*
import fleet.util.logging.logger
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

object NoSupervisorForSagas : CoroutineContext.Element, CoroutineContext.Key<NoSupervisorForSagas> {
  override val key: CoroutineContext.Key<*> get() = this
}

data class SagaScopeEntity(override val eid: EID) : Entity {
  companion object : EntityType<SagaScopeEntity>(SagaScopeEntity::class, ::SagaScopeEntity) {
    val logger = logger<SagaScopeEntity>()
    val SagaScopeAttr = requiredTransient<CoroutineScope>("sagaScope")
    val KernelAttr = requiredTransient<Transactor>("kernel", Indexing.UNIQUE)
  }

  val sagaScope by SagaScopeAttr

  val transactor by KernelAttr
}

suspend fun sagaScope(body: suspend CoroutineScope.(CoroutineScope) -> Unit) {
  coroutineScope {
    resource { cc ->
      spannedScope("sagaScope") {
        when {
          coroutineContext[NoSupervisorForSagas] != null ->
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
}
