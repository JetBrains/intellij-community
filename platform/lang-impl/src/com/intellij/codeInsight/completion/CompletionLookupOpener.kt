// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.ApiStatus

/**
 * A singleton that provides the functionality to open the code completion lookups pop-up.
 *
 * @see [LookupImpl]
 * @see [CompletionProgressIndicator]
 */
@ApiStatus.Internal
object CompletionLookupOpener {
  private fun LookupImpl.shownOrDisposed() = this.shownTimestampMillis != 0L || this.isLookupDisposed

  /**
   * Schedules a request to open lookup on the AWT event dispatching thread under Write Intent lock.
   * The request expires, if the lookup is already opened.
   */
  fun showLookup(parameters: CompletionParameters) {
    val process = parameters.process
    if (process !is CompletionProgressIndicator) {
      return
    }
    val lookup = process.lookup
    if (lookup.shownOrDisposed()) return
    ApplicationManager.getApplication().invokeLater(process::showLookup) { lookup.shownOrDisposed() }
  }
}