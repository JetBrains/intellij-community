// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision.ui.model

import com.intellij.util.application
import com.intellij.util.ui.EDT
import com.jetbrains.rd.util.reactive.ExecutionOrder
import com.jetbrains.rd.util.reactive.IScheduler
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object SwingScheduler : IScheduler {
  override val executionOrder: ExecutionOrder
    get() = ExecutionOrder.Sequential

  override fun flush() {
    application.invokeAndWait {}
  }

  override fun queue(action: () -> Unit) {
    application.invokeLater { action() }
  }

  override val isActive: Boolean
    get() = EDT.isCurrentThreadEdt()
}