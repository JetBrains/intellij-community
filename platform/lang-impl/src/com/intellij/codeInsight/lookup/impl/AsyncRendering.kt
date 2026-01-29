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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal class AsyncRendering(
  private val coroutineScope: CoroutineScope,
  private val renderingCallback: () -> Unit,
) {
  // Use a maximum of three concurrent rendering jobs to not overload the CPU unnecessarily.
  private val renderersSemaphore = Semaphore(3)

  /**
   * Set the presentation for the lookup element. Overwrites previous presentation.
   */
  fun cachePresentation(element: LookupElement, presentation: LookupElementPresentation) {
    element.putUserData(LAST_COMPUTED_PRESENTATION, presentation)
  }

  /**
   * @return cached presentation of the lookup element
   */
  fun getCachedPresentation(element: LookupElement): LookupElementPresentation =
    element.getUserData(LAST_COMPUTED_PRESENTATION)!!

  /**
   * Cancels the rendering job for the given lookup element if it exists.
   */
  fun cancelRendering(item: LookupElement) {
    synchronized(LAST_COMPUTATION) {
      val job = item.getUserData(LAST_COMPUTATION) ?: return
      job.cancel()
      item.putUserData(LAST_COMPUTATION, null)
    }
  }

  /**
   * Schedule rendering for the lookup element.
   * The new value will overwrite the previously cached presentation.
   */
  fun scheduleRendering(element: LookupElement, renderer: LookupElementRenderer<LookupElement>) {
    synchronized(LAST_COMPUTATION) {
      cancelRendering(element)

      if (!coroutineScope.isActive) {
        return
      }

      val job = coroutineScope.launch {
        // If we use a limited dispatcher, `readAction` (and other calls) would redirect the coroutine to the `Dispatcher.default`
        // (or other dispatchers), leaving the limited dispatcher free. The next coroutine would then be processed on the limited dispatcher
        // and so on. Ultimately, this could spin a new dispatcher worker thread for almost each item on the list overloading the coroutine
        // scheduler and coroutine pool. To mitigate this issue, we can use a semaphore, which will allow for a maximum of three concurrent
        // rendering jobs.

        renderersSemaphore.withPermit {
          val job = coroutineContext.job

          if (renderer is SuspendingLookupElementRenderer<LookupElement>) {
            renderInBackgroundSuspending(element, renderer)
          }
          else {
            readAction {
              if (element.isValid) {
                renderInBackground(element, renderer)
              }
            }
          }
          synchronized(LAST_COMPUTATION) {
            element.replace(LAST_COMPUTATION, job, null)
          }
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
    cachePresentation(element, presentation)
    renderingCallback()
  }

  private suspend fun renderInBackgroundSuspending(element: LookupElement, renderer: SuspendingLookupElementRenderer<LookupElement>) {
    val presentation = LookupElementPresentation()
    renderer.renderElementSuspending(element, presentation)

    presentation.freeze()
    cachePresentation(element, presentation)
    renderingCallback()
  }
}

private val LAST_COMPUTED_PRESENTATION = Key.create<LookupElementPresentation>("LAST_COMPUTED_PRESENTATION")
private val LAST_COMPUTATION = Key.create<Job>("LAST_COMPUTATION")
