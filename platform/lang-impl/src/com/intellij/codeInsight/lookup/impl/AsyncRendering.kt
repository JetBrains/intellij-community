// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalCoroutinesApi::class)

package com.intellij.codeInsight.lookup.impl

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementRenderer
import com.intellij.codeInsight.lookup.SuspendingLookupElementRenderer
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.Key
import com.intellij.util.indexing.DumbModeAccessType
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

internal class AsyncRendering(private val lookup: LookupImpl) {
  companion object {
    private val LAST_COMPUTED_PRESENTATION = Key.create<LookupElementPresentation>("LAST_COMPUTED_PRESENTATION")
    private val LAST_COMPUTATION = Key.create<Job>("LAST_COMPUTATION")
    private val limitedDispatcher = Dispatchers.Default.limitedParallelism(1)

    fun rememberPresentation(element: LookupElement, presentation: LookupElementPresentation) {
      element.putUserData(LAST_COMPUTED_PRESENTATION, presentation)
    }

    fun cancelRendering(item: LookupElement) {
      synchronized(LAST_COMPUTATION) {
        val job = item.getUserData(LAST_COMPUTATION) ?: return
        job.cancel()
        item.putUserData(LAST_COMPUTATION, null)
      }
    }
  }

  private val nonSuspendingRenderersMutex = Mutex(false)
  private val suspendingRenderersSemaphore = Semaphore(10)

  fun getLastComputed(element: LookupElement): LookupElementPresentation = element.getUserData(LAST_COMPUTED_PRESENTATION)!!

  fun scheduleRendering(element: LookupElement, renderer: LookupElementRenderer<LookupElement>) {
    synchronized(LAST_COMPUTATION) {
      cancelRendering(element)

      if (lookup.isLookupDisposed) {
        return
      }

      val job = lookup.coroutineScope.launch(limitedDispatcher) {
        val job = coroutineContext.job
        if (renderer is SuspendingLookupElementRenderer<LookupElement>) {
          // Suspending renderers work on the `limitedDispatcher`, so there is no need to limit the throughput.
          // However, the user code may still move the coroutine to a different dispatcher, so just in case
          // limit the throughput.
          suspendingRenderersSemaphore.withPermit {
            renderInBackgroundSuspending(element, renderer)
          }
        } else {
          // `readAction` redirects the coroutine to the `Dispatcher.default` leaving `limitedDispatcher` free.
          // The next coroutine is then processed on the `limitedDispatcher` and so on. Ultimately, this can spin
          // a new dispatcher worker thread for almost each item on the list. We should only allow a single
          // non-suspending renderer to run within the read action.
          nonSuspendingRenderersMutex.withLock {
            readAction {
              if (element.isValid) {
                renderInBackground(element, renderer)
              }
            }
          }
        }
        synchronized(LAST_COMPUTATION) {
          element.replace(LAST_COMPUTATION, job, null)
        }
      }
      element.putUserData(LAST_COMPUTATION, job)
    }
  }

  private fun renderInBackground(element: LookupElement, renderer: LookupElementRenderer<LookupElement>) {
    val presentation = LookupElementPresentation()
    DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode {
      renderer.renderElement(element, presentation)
    }

    presentation.freeze()
    rememberPresentation(element, presentation)
    lookup.cellRenderer.scheduleUpdateLookupWidthFromVisibleItems()
  }

  private suspend fun renderInBackgroundSuspending(element: LookupElement, renderer: SuspendingLookupElementRenderer<LookupElement>) {
    val presentation = LookupElementPresentation()
    renderer.renderElementSuspending(element, presentation)

    presentation.freeze()
    rememberPresentation(element, presentation)
    lookup.cellRenderer.scheduleUpdateLookupWidthFromVisibleItems()
  }


}
