// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.util.indexing.FindSymbolParameters
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The runs of one goto search in Search Everywhere. Each run searches again with a bigger limit.
 *
 * A contributor that honors [FindSymbolParameters.getLimit] produces its best `limit + 1` items when it has more, and
 * calls [FindSymbolParameters.reportCut] ([markCut]). The filters may then thin the answer below the limit, so the next
 * run doubles the limit. A run that nobody cut is the last one. A contributor that ignores the limit never reports a cut,
 * so it costs one run: its answer is already full. A specific query stays at one run.
 *
 * A run starts only after the collector took every item of the run before: in Search Everywhere, after the user scrolled
 * to the end, or after the section took fewer items than its limit. The runs keep the best weight sent for each item, so
 * the collector gets an item again only with a better weight: across runs, across restarted attempts, and across
 * contributors that find the same item. A better copy matters: the file provider sends a path match first, then the same
 * file again with a qualifier bonus, and the collector replaces the equal item it has.
 */
@ApiStatus.Internal
class GotoLimitedRuns(firstLimit: Int) {
  /** The limit of the current run. */
  @Volatile
  var limit: Int = firstLimit
    private set

  /** The number of the current run, starting at 1. */
  @Volatile
  var runNumber: Int = 1
    private set

  /** The best weight sent to the collector for each item. A goto model can call its consumer from several threads. */
  private val sent = ConcurrentHashMap<Any, Int>()
  private val cutInRun = AtomicBoolean(false)

  /** Starts an attempt of the current run: a restarted read action reports its cuts again. */
  fun startAttempt() {
    cutInRun.set(false)
  }

  /** The sink of [FindSymbolParameters.withLimit]: a contributor had more than [limit] items. */
  fun markCut() {
    cutInRun.set(true)
  }

  /** Returns true when no run sent [item] yet, or sent it with a lower weight, and marks it sent with [weight]. */
  fun accept(item: Any, weight: Int): Boolean {
    var accepted = false
    sent.compute(item) { _, sentWeight ->
      if (sentWeight == null || weight > sentWeight) {
        accepted = true
        weight
      }
      else sentWeight
    }
    return accepted
  }

  /**
   * Undoes [accept] when the delivery of [item] with [weight] was cancelled, so a restarted attempt sends it again.
   * A lower copy sent before may come again then; the collector takes an equal item once.
   */
  fun forget(item: Any, weight: Int) {
    sent.remove(item, weight)
  }

  /**
   * Calls [run] with the limit of each run, until a run cut nobody.
   * [run] returns false when the collector stopped, and then no other run starts.
   */
  inline fun collect(run: (limit: Int) -> Boolean) {
    while (true) {
      if (!run(limit) || !next()) return
    }
  }

  /** A cut reported at [FindSymbolParameters.UNLIMITED] ends the runs too: a contributor bug must not spin. */
  @PublishedApi
  internal fun next(): Boolean {
    if (limit == FindSymbolParameters.UNLIMITED || !cutInRun.getAndSet(false)) return false

    limit = if (limit >= FindSymbolParameters.UNLIMITED / 2) FindSymbolParameters.UNLIMITED else limit * 2
    runNumber++
    return true
  }
}
