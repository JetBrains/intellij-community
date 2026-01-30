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

/**
 * Async rendering of lookup elements.
 *
 * When a lookup element is added to lookup, its fast presentation ([LookupElement.renderElement]) is cached via [cachePresentation].
 *
 * When the lookup decides that a given lookup element is going be shown in viewport, it schedules slow rendering via [scheduleRendering].
 *
 * If the lookup removes a lookup element (e.g., because the limit of results is reached), the computation is canceled via [cancelRendering].
 *
 * Cached presentation can be retrieved via [getCachedPresentation].
 */
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
  fun scheduleRendering(element: LookupElement) {
    val renderer = element.expensiveRendererImpl ?: return

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
    doRender(element) { presentation ->
      DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode {
        renderer.renderElement(element, presentation)
      }
    }
  }

  private suspend fun renderInBackgroundSuspending(element: LookupElement, renderer: SuspendingLookupElementRenderer<LookupElement>) {
    doRender(element) { presentation ->
      renderer.renderElementSuspending(element, presentation)
    }
  }

  private inline fun doRender(
    element: LookupElement,
    computation: (presentation: LookupElementPresentation) -> Unit
  ) {
    val presentation = LookupElementPresentation()
    computation(presentation)
    presentation.freeze()
    cachePresentation(element, presentation)
    renderingCallback()
  }
}

@Suppress("UNCHECKED_CAST")
private val LookupElement.expensiveRendererImpl: LookupElementRenderer<LookupElement>?
  get() = this.expensiveRenderer as? LookupElementRenderer<LookupElement>

private val LAST_COMPUTED_PRESENTATION = Key.create<LookupElementPresentation>("LAST_COMPUTED_PRESENTATION")
private val LAST_COMPUTATION = Key.create<Job>("LAST_COMPUTATION")
