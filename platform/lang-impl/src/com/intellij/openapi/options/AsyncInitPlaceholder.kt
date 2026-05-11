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
import org.jetbrains.annotations.TestOnly
import java.awt.BorderLayout
import javax.swing.JComponent

/**
 * Creates a placeholder:
 * 1. A loading icon is shown.
 * 2. Initialization in [init] is started asynchronously after the placeholder is shown.
 * 3. Once initialization is complete, the placeholder content is replaced on the EDT with the result of [onInitialized].
 */
@ApiStatus.Internal
class AsyncInitPlaceholder(
  private val init: suspend () -> Unit,
  private val onInitialized: () -> JComponent,
  parentDisposable: Disposable,
  debugName: String,
) : JBLoadingPanel(BorderLayout(), parentDisposable) {

  init {
    startLoading()

    val job = initOnShow(debugName) {
      startAsyncInitializationImpl()
    }

    parentDisposable.whenDisposed {
      job.cancel()
    }
  }

  @TestOnly
  suspend fun startAsyncInitialization() {
    startAsyncInitializationImpl()
  }

  private suspend fun startAsyncInitializationImpl() {
    withContext(Dispatchers.IO) { init() }
    withContext(Dispatchers.EDT) {
      val content = onInitialized()
      stopLoading()
      add(content)
    }
  }
}
