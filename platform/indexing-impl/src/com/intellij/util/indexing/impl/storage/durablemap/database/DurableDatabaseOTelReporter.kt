// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage.durablemap.database

import com.intellij.platform.util.io.storages.database.spi.metrics.DatabaseMetrics
import com.intellij.platform.util.io.storages.database.spi.metrics.DatabaseMetricsProvider
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.metrics.ObservableLongMeasurement
import org.jetbrains.annotations.ApiStatus
import java.util.Collections
import java.util.IdentityHashMap

/** Reports database metrics and preserves cumulative counters after databases close */
@ApiStatus.Internal
class DurableDatabaseOTelReporter(private val meter: Meter) : AutoCloseable {
  private val lock = Any()
  private val databases = Collections.newSetFromMap(IdentityHashMap<DatabaseMetricsProvider, Boolean>())
  private var closed = false

  private val gauges = listOf(
    gauge("catalog.storesTotal") { it.catalog().storesTotal().toLong() },
    gauge("chunks.total") { it.chunks().total().toLong() },
    gauge("chunks.activeCurrent") { it.chunks().activeCurrent().toLong() },
    gauge("chunks.sealedCurrent") { it.chunks().sealedCurrent().toLong() },
    gauge("chunks.retiredCurrent") { it.chunks().retiredCurrent().toLong() },
    gauge("chunks.totalMappedBytes", "bytes") { it.chunks().totalMappedBytes() },
    gauge("blocks.total") { it.blocks().total().toLong() },
    gauge("blocks.allocatedCurrent") { it.blocks().allocatedCurrent().toLong() },
    gauge("blocks.activeCurrent") { it.blocks().activeCurrent().toLong() },
    gauge("blocks.sealedCurrent") { it.blocks().sealedCurrent().toLong() },
    gauge("blocks.retiredCurrent") { it.blocks().retiredCurrent().toLong() },
  )

  private val counters = listOf(
    counter("catalog.storesCreated") { it.catalog().storesCreated().toLong() },
    counter("catalog.storesDropped") { it.catalog().storesDropped().toLong() },
    counter("catalog.catalogLogRecoveries") { it.catalog().catalogLogRecoveries().toLong() },
    counter("chunks.created") { it.chunks().created() },
    counter("chunks.opened") { it.chunks().opened() },
    counter("chunks.sealed") { it.chunks().sealed() },
    counter("chunks.retired") { it.chunks().retired() },
    counter("chunks.released") { it.chunks().released() },
    counter("chunks.statesReconciled") { it.chunks().statesReconciled() },
    counter("chunks.tailsRolledBack") { it.chunks().tailsRolledBack() },
    counter("chunks.tailBytesRolledBack", "bytes") { it.chunks().tailBytesRolledBack() },
    counter("chunks.filesDeleted") { it.chunks().filesDeleted() },
    counter("blocks.allocated") { it.blocks().allocated() },
    counter("blocks.activated") { it.blocks().activated() },
    counter("blocks.discarded") { it.blocks().discarded() },
    counter("blocks.sealed") { it.blocks().sealed() },
    counter("blocks.retired") { it.blocks().retired() },
    counter("blocks.evacuated") { it.blocks().evacuated() },
    counter("blocks.incompleteBlocksDiscarded") { it.blocks().incompleteBlocksDiscarded() },
    counter("blocks.droppedStoreBlocksRetired") { it.blocks().droppedStoreBlocksRetired() },
    counter("blocks.staleBlockCopiesRetired") { it.blocks().staleBlockCopiesRetired() },
  )

  private val callback = meter.batchCallback(
    { collect() },
    gauges.first().observer,
    *(gauges.drop(1) + counters).map { it.observer }.toTypedArray(),
  )

  fun attachDatabase(database: DatabaseMetricsProvider) {
    synchronized(lock) {
      check(!closed) { "The metrics reporter is closed" }
      check(databases.add(database)) { "The database is already attached" }
    }
  }

  /** Stops database reads before its resources close and saves counters for the next opening */
  fun detachDatabase(database: DatabaseMetricsProvider) {
    synchronized(lock) {
      if (!databases.remove(database)) return
      val metrics = database.metrics(false)
      for (counter in counters) {
        counter.completed += counter.value(metrics)
      }
    }
  }

  private fun collect() {
    synchronized(lock) {
      if (closed) return
      val metrics = databases.map { it.metrics(false) }
      if (metrics.isNotEmpty()) {
        for (gauge in gauges) {
          gauge.observer.record(metrics.sumOf { gauge.value(it) })
        }
      }
      for (counter in counters) {
        counter.observer.record(counter.completed + metrics.sumOf { counter.value(it) })
      }
    }
  }

  override fun close() {
    synchronized(lock) {
      if (closed) return
      closed = true
      databases.clear()
    }
    // Do not hold the lock while the SDK removes a callback that can be running
    callback.close()
  }

  private fun gauge(name: String, unit: String = "1", value: (DatabaseMetrics) -> Long): Metric =
    Metric(meter.gaugeBuilder("DurableDatabase.$name").setUnit(unit).ofLongs().buildObserver(), value)

  private fun counter(name: String, unit: String = "1", value: (DatabaseMetrics) -> Long): Metric =
    Metric(meter.counterBuilder("DurableDatabase.$name").setUnit(unit).buildObserver(), value)

  private class Metric(val observer: ObservableLongMeasurement, val value: (DatabaseMetrics) -> Long) {
    /**
     * Last value of 'counter'-type metric is stored here before DB disconnect, so after following reconnect counter value
     * is not suddenly jumping.
     * I.e. counter-type metric expects diffs from previous value, while during DB reload reported value is usually started from 0.
     */
    var completed: Long = 0
  }
}
