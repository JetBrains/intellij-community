package com.intellij.codeInsight.codeVision.ui.model

import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.util.ui.EDT
import com.jetbrains.rd.util.reactive.ExecutionOrder
import com.jetbrains.rd.util.reactive.IScheduler
import org.jetbrains.annotations.ApiStatus
import javax.swing.SwingUtilities

@ApiStatus.Internal
object SwingScheduler : IScheduler {
  override val executionOrder: ExecutionOrder
    get() = ExecutionOrder.Sequential

  override fun flush() {
    SwingUtilities.invokeAndWait {}
  }

  override fun queue(action: () -> Unit) {
    SwingUtilities.invokeLater { WriteIntentReadAction.run { action() } }
  }

  override val isActive: Boolean
    get() = EDT.isCurrentThreadEdt()
}