// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.ide.impl

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.ide.SelectInContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

/**
 * Fetches the data that [com.intellij.ide.SelectInTarget.canSelect] needs, before Select In calls it.
 *
 * [com.intellij.ide.SelectInTarget.canSelect] runs in a read action, often in an action update, so it must not wait for an RPC.
 * A prefetcher gets the data before that, outside any read action, and keeps it for the [SelectInContext].
 */
@ApiStatus.Internal
interface SelectInPrefetcher {
  /**
   * Tells if [prefetch] has work for the [context].
   * Select In calls it on any thread, also on the EDT before the popup shows. So it must be fast and must not wait for an RPC.
   */
  fun isNeeded(context: SelectInContext): Boolean

  /**
   * Fetches the data for the [context]. Select In calls it outside a read action and not on the EDT.
   * The Select In popup waits for it, so it must give up after a short timeout.
   */
  suspend fun prefetch(context: SelectInContext)
}

private val EP_NAME = ExtensionPointName<SelectInPrefetcher>("com.intellij.selectInPrefetcher")

/** Returns the prefetchers that have work for the [context]. */
fun getSelectInPrefetchers(context: SelectInContext): List<SelectInPrefetcher> {
  return EP_NAME.extensionList.filter { prefetcher ->
    try {
      prefetcher.isNeeded(context)
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      LOG.error("$prefetcher failed to tell if it has work for $context", e)
      false
    }
  }
}

/** Runs the [prefetchers] for the [context] at the same time. A failed prefetcher does not stop the others. */
suspend fun prefetchForSelectIn(context: SelectInContext, prefetchers: List<SelectInPrefetcher>) {
  coroutineScope {
    for (prefetcher in prefetchers) {
      launch {
        try {
          prefetcher.prefetch(context)
        }
        catch (e: Throwable) {
          rethrowControlFlowException(e)
          LOG.error("$prefetcher failed to fetch the data for $context", e)
        }
      }
    }
  }
}

/**
 * Runs the [prefetchers] for the [context], and waits the same way as an action popup waits for its updates.
 * The EDT keeps working, and a loading icon shows after a delay.
 *
 * A key press, a mouse press or a focus loss of the window cancels the wait.
 * Then this function throws a [com.intellij.openapi.progress.ProcessCanceledException].
 */
@RequiresEdt
fun prefetchForSelectInWithProgressIcon(
  dataContext: DataContext,
  place: String,
  context: SelectInContext,
  prefetchers: List<SelectInPrefetcher>,
) {
  Utils.computeWithProgressIcon(dataContext, place) {
    prefetchForSelectIn(context, prefetchers)
  }
}

private val LOG = fileLogger()
