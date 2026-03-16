// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup

import com.intellij.openapi.progress.runBlockingCancellable

abstract class SuspendingLookupElementRenderer<T : LookupElement> : LookupElementRenderer<T>() {
  /**
   * Render LookupElement in a coroutine. There is no guarantee that the element is valid.
   * The method will usually be called without a read lock. However, if consumer calls it
   * through `renderElement` method, there might be a read lock for which implementation should be prepared.
   *
   * You may expect that the coroutine is called simultaneously
   * from multiple coroutines for several different elements.
   */
  abstract suspend fun renderElementSuspending(element: T, presentation: LookupElementPresentation)

  final override fun renderElement(element: T, presentation: LookupElementPresentation) {
    runBlockingCancellable { renderElementSuspending(element, presentation) }
  }
}
