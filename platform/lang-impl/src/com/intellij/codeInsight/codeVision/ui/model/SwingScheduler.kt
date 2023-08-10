package com.intellij.codeInsight.codeVision.ui.model

import com.jetbrains.rd.util.reactive.IScheduler
import javax.swing.SwingUtilities

object SwingScheduler : IScheduler {
  override fun flush() {
    SwingUtilities.invokeAndWait {}
  }

  override fun queue(action: () -> Unit) {
    SwingUtilities.invokeLater { action() }
  }

  override val isActive: Boolean
    get() = SwingUtilities.isEventDispatchThread()
}