package com.intellij.codeInsight.codeVision.ui.model

import com.jetbrains.rd.util.reactive.IScheduler
import com.jetbrains.rd.util.reactive.ExecutionOrder
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
    SwingUtilities.invokeLater { action() }
  }

  override val isActive: Boolean
    get() = SwingUtilities.isEventDispatchThread()
}