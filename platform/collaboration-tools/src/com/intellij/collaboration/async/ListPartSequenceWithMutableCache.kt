// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.async

import com.intellij.collaboration.util.ComputableSequence
import com.intellij.collaboration.util.ListPart
import com.intellij.collaboration.util.SequenceComputer
import com.intellij.collaboration.util.SequenceComputer.ComputationOutcome
import com.intellij.collaboration.util.SequenceItem
import com.intellij.openapi.progress.checkCanceled
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.concurrency.SynchronizedClearableLazy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ListPartSequenceWithMutableCache<T>(
  parentCs: CoroutineScope,
  private val sequence: ComputableSequence<ListPart<T>>,
) : ComputableSequence<ListPart<T>> {
  private val sharedSequence = SynchronizedClearableLazy {
    SharedSequence(parentCs, sequence.getComputer())
  }

  override fun getComputer(): SequenceComputer<ListPart<T>> {
    return sharedSequence.value.getComputer()
  }

  suspend fun updateItem(updater: (T) -> T?) {
    sharedSequence.valueIfInitialized?.updateItem(updater)
  }

  fun reset() {
    sharedSequence.dropSynchronously()?.cancel()
  }

  private class SharedSequence<T>(
    parentCs: CoroutineScope,
    private val computer: SequenceComputer<ListPart<T>>,
  ) : ComputableSequence<ListPart<T>> {
    private val cs = parentCs.childScope(javaClass.name)

    private var nextComputation = scheduleLoadNext()
    private val loadedParts = mutableListOf<ListPart<T>>()
    private val stateGuard = Mutex()

    private fun scheduleLoadNext(): Deferred<ComputationOutcome<ListPart<T>>> =
      cs.async(start = CoroutineStart.LAZY) {
        val result = computer.computeNext()
        checkCanceled()
        stateGuard.withLock {
          checkCanceled()
          if (result is ComputationOutcome.Item) {
            loadedParts.add(result.value)
          }
          nextComputation = scheduleLoadNext()
        }
        result
      }

    suspend fun updateItem(updater: (T) -> T?) {
      if (!cs.isActive) return
      stateGuard.withLock {
        val partIterator = loadedParts.listIterator()
        while (partIterator.hasNext()) {
          val part = partIterator.next()
          val list = part.value
          var updatedList: List<T>? = null
          for (idx in list.indices) {
            val newValue = updater(list[idx]) ?: continue
            updatedList = list.toMutableList().apply { set(idx, newValue) }
            break
          }
          if (updatedList == null) continue
          partIterator.set(SequenceItem(updatedList, part.isLast))
          return@withLock
        }
      }
    }

    override fun getComputer(): SequenceComputer<ListPart<T>> {
      return object : SequenceComputer<ListPart<T>> {
        private var emittedParts = 0

        override suspend fun computeNext(): ComputationOutcome<ListPart<T>> {
          return try {
            cs.ensureActive()
            grabCachedPart() ?: run {
              // Nothing cached yet: wait for the next part to be computed and then serve it from the cache, so that
              // `emittedParts` is advanced exactly once. Returning the awaited outcome directly would leave `emittedParts`
              // behind and re-emit the same part from the cache on the following call.
              nextComputation.await()
              grabCachedPart()
            } ?: ComputationOutcome.Done
          }
          catch (@Suppress("IncorrectCancellationExceptionHandling") _: CancellationException) {
            checkCanceled() // don't cancel the caller
            ComputationOutcome.Done
          }
        }

        private suspend fun grabCachedPart() =
          stateGuard.withLock {
            val loadedPart = loadedParts.getOrNull(emittedParts)
            if (loadedPart != null) {
              emittedParts++
              ComputationOutcome.Item(loadedPart)
            }
            else {
              null
            }
          }
      }
    }

    fun cancel() {
      cs.cancel()
    }
  }
}
