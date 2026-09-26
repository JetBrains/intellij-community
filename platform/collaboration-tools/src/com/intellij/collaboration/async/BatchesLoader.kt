// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.async

import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformWhile
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration

/**
 * A utility class for incremental batch loading from paginated APIs with lazy initialization.
 *
 * This class wraps a [Flow] of batches (pages) and provides:
 * - **Lazy initialization**: Loading starts only when [getBatches] is first collected
 * - **Shared flow**: Multiple collectors share the same loading process via [SharedFlow]
 * - **Cancellation support**: Loading can be cancelled and restarted via [cancel]
 *
 * ## Usage example
 * ```kotlin
 * // Create a loader with a flow that emits batches of items
 * val loader = BatchesLoader(coroutineScope, paginatedApiFlow)
 *
 * // Collect batches incrementally as they arrive
 * loader.getBatches().collect { batch ->
 *     items.addAll(batch)
 *     updateUI(items)
 * }
 *
 * // Cancel loading and allow restart
 * loader.cancel()
 * ```
 *
 * @param T the type of items in each batch
 * @param cs the parent [CoroutineScope] for managing the loading lifecycle
 * @param batchesFlow a [Flow] that emits batches (lists) of items, typically from a paginated API
 * @param stopTimeout a delay between the disappearance of the last subscriber and the stopping of the sharing coroutine
 * and cleaning up the replay cache. If not specified, the sharing coroutine will never be stopped.
 */
@ApiStatus.Internal
class BatchesLoader<T>(private val cs: CoroutineScope, private val batchesFlow: Flow<List<T>>, private val stopTimeout: Duration? = null) {
  private var flowAndScope: Pair<SharedFlow<BatchesLoadingState<T>>, CoroutineScope>? = null

  /**
   * Returns a [Flow] that emits batches of items as they are loaded.
   *
   * Each emission contains only the newly loaded items since the last emission.
   * The flow completes when all batches are loaded or throws if an error occurs.
   */
  fun getBatches(): Flow<List<T>> {
    var currentPagesCount = 0
    return startLoading().transformWhile {
      if (it.pages.size > currentPagesCount) {
        emit(it.pages.subList(currentPagesCount, it.pages.size).flatten())
        currentPagesCount = it.pages.size
      }
      when (it) {
        is BatchesLoadingState.Loading -> true
        is BatchesLoadingState.Loaded -> false
        is BatchesLoadingState.Cancelled -> throw it.ce
        is BatchesLoadingState.Error -> throw it.error
      }
    }
  }

  @Synchronized
  private fun startLoading(): SharedFlow<BatchesLoadingState<T>> {
    flowAndScope?.run {
      return first
    }

    val sharingScope = cs.childScope(javaClass.name)
    val sharingStarted = if (stopTimeout == null) SharingStarted.Lazily else
      SharingStarted.WhileSubscribed(stopTimeout.inWholeMilliseconds, 0)
    val sharedFlow = flow {
      val loadedBatches = mutableListOf<List<T>>()
      try {
        batchesFlow.flowOn(Dispatchers.IO).collect { batch ->
          loadedBatches.add(batch)
          emit(BatchesLoadingState.Loading(loadedBatches.toList()))
        }
        // will never change anymore, so it's fine to emit as-is
        emit(BatchesLoadingState.Loaded(loadedBatches))
      }
      catch (ce: CancellationException) {
        emit(BatchesLoadingState.Cancelled(loadedBatches, ce))
        throw ce
      }
      catch (e: Exception) {
        emit(BatchesLoadingState.Error(loadedBatches, e))
      }
    }.shareIn(sharingScope, sharingStarted, 1)
    flowAndScope = sharedFlow to sharingScope
    return sharedFlow
  }

  /**
   * Cancels the current loading process and resets the loader state.
   *
   * After calling this method, the next call to [getBatches] will start a fresh loading process.
   */
  @Synchronized
  fun cancel() {
    flowAndScope?.second?.cancel()
    flowAndScope = null
  }

  private sealed class BatchesLoadingState<T>(val pages: List<List<T>>) {
    class Loading<T>(pages: List<List<T>>) : BatchesLoadingState<T>(pages)
    class Loaded<T>(pages: List<List<T>>) : BatchesLoadingState<T>(pages)
    class Error<T>(pages: List<List<T>>, val error: Exception) : BatchesLoadingState<T>(pages)
    class Cancelled<T>(pages: List<List<T>>, val ce: CancellationException) : BatchesLoadingState<T>(pages)
  }
}
