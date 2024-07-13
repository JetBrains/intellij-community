// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup.impl

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupElementRenderer
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.SequentialTaskExecutor
import com.intellij.util.indexing.DumbModeAccessType
import org.jetbrains.concurrency.CancellablePromise

internal class AsyncRendering(private val lookup: LookupImpl) {
  companion object {
    private val LAST_COMPUTED_PRESENTATION = Key.create<LookupElementPresentation>("LAST_COMPUTED_PRESENTATION")
    private val LAST_COMPUTATION = Key.create<CancellablePromise<*>>("LAST_COMPUTATION")
    private val executor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("ExpensiveRendering")

    fun rememberPresentation(element: LookupElement, presentation: LookupElementPresentation) {
      element.putUserData(LAST_COMPUTED_PRESENTATION, presentation)
    }

    fun cancelRendering(item: LookupElement) {
      synchronized(LAST_COMPUTATION) {
        val promise = item.getUserData(LAST_COMPUTATION) ?: return
        promise.cancel()
        item.putUserData(LAST_COMPUTATION, null)
      }
    }
  }

  fun getLastComputed(element: LookupElement): LookupElementPresentation = element.getUserData(LAST_COMPUTED_PRESENTATION)!!

  fun scheduleRendering(element: LookupElement, renderer: LookupElementRenderer<LookupElement>) {
    synchronized(LAST_COMPUTATION) {
      cancelRendering(element)
      var promiseRef: CancellablePromise<*>? = null
      val promise = ReadAction.nonBlocking {
          if (element.isValid) {
            renderInBackground(element, renderer)
          }
          synchronized(LAST_COMPUTATION) {
            element.replace(LAST_COMPUTATION, promiseRef, null)
          }
        }
        .expireWith(lookup)
        .submit(executor)
      element.putUserData(LAST_COMPUTATION, promise)
      promiseRef = promise
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
}
