// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.kernel.rete.impl

import com.jetbrains.rhizomedb.Attribute
import com.jetbrains.rhizomedb.EID
import fleet.kernel.rete.DatomPort
import fleet.kernel.rete.Producer
import fleet.kernel.rete.Query
import fleet.kernel.rete.RevalidationPort
import fleet.util.logging.KLogger
import fleet.fastutil.longs.LongSet
import kotlin.coroutines.CoroutineContext

data class QueryTracingKey(val tracingKey: Any,
                           val logger: KLogger) : CoroutineContext.Element {
  companion object : CoroutineContext.Key<QueryTracingKey>

  override val key: CoroutineContext.Key<*>
    get() = QueryTracingKey

  override fun toString(): String = "[${tracingKey}]"
}


internal fun <T> Query<T>.tracing(trackingKey: QueryTracingKey?): Query<T> = let { query ->
  when (trackingKey) {
    null -> query
    else -> Query {
      val queryId = query.hashCode()
      trackingKey.logger.info { "${trackingKey}: query $queryId is added to the network" }
      onDispose {
        trackingKey.logger.info { "${trackingKey}: query $queryId is removed from the network" }
      }
      val producer = query.producer()
      Producer { emit ->
        val collectorId = emit.hashCode()
        trackingKey.logger.info { "${trackingKey}: query $queryId is being collected by $collectorId" }
        onDispose {
          trackingKey.logger.info { "${trackingKey}: query $queryId has stopped being collected by $collectorId" }
        }
        producer.collect { token ->
          trackingKey.logger.info { "${trackingKey}: query $queryId emits token $token to $collectorId" }
          emit(token)
        }
      }
    }
  }
}

internal fun RevalidationPort.tracing(patterns: LongSet, tracingKey: QueryTracingKey?): RevalidationPort = let { port ->
  tracingKey?.logger?.info { "${tracingKey}: subscribing revalidation port $port with patterns $patterns" }
  when (tracingKey) {
    null -> port
    else -> RevalidationPort {
      port.revalidate().also { newPatterns ->
        tracingKey.logger.info { "${tracingKey}: propagated revalidation towards the port $port, newPatterns: $newPatterns" }
      }
    }
  }
}

internal fun DatomPort.tracing(e: EID?, attr: Attribute<*>?, v: Any?, tracingKey: QueryTracingKey?): DatomPort = let { port ->
  when (tracingKey) {
    null -> port
    else -> {
      tracingKey.logger.info { "${tracingKey}: subscribing datom port $port to pattern [$e, $attr, $v]" }
      DatomPort { datom ->
        tracingKey.logger.info { "${tracingKey}: propagating $datom towards the port $port" }
        port.feedDatom(datom)
      }
    }
  }
}

