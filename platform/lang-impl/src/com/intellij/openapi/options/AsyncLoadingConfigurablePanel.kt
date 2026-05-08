// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.ui.initOnShow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import javax.swing.JComponent

/**
 * Creates a placeholder:
 * 1. A loading icon is shown.
 * 2. Initialization in [load] is started asynchronously after the placeholder is shown.
 * 3. Once initialization is complete, the placeholder content is replaced on the EDT with the result of [onLoaded].
 */
@ApiStatus.Internal
fun <T> createAsyncInitPlaceholder(
  load: suspend () -> T,
  onLoaded: () -> JComponent,
  parentDisposable: Disposable,
  debugName: String,
): JComponent {
  val result = JBLoadingPanel(BorderLayout(), parentDisposable)
  result.startLoading()

  val job = result.initOnShow(debugName) {
    withContext(Dispatchers.IO) { load() }
    withContext(Dispatchers.EDT) {
      val content = onLoaded()
      result.stopLoading()
      result.add(content)
    }
  }

  parentDisposable.whenDisposed {
    job.cancel()
  }

  return result
}
