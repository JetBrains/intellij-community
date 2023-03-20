// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.ui

import com.intellij.ide.util.DelegatingProgressIndicator
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase
import com.intellij.openapi.util.NlsContexts
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JProgressBar
import javax.swing.SwingUtilities
import kotlin.math.min

class TransferSettingsProgressIndicatorBase(
  private val progressBar: JProgressBar,
  private val statusLabel: JLabel,
  private val modalityComponent: JComponent
) : AbstractProgressIndicatorExBase(true) {

  override fun getModalityState(): ModalityState {
    return ModalityState.stateForComponent(modalityComponent)
  }

  override fun setText2(text: String?) {
    super.setText2(text)
    SwingUtilities.invokeLater {
      statusLabel.text = text
    }
  }

  override fun setFraction(fraction: Double) {
    super.setFraction(fraction)
    SwingUtilities.invokeLater {
      val value = (100 * fraction + .5).toInt()
      progressBar.value = value
    }
  }

  override fun setIndeterminate(indeterminate: Boolean) {
    super.setIndeterminate(indeterminate)
    SwingUtilities.invokeLater {
      progressBar.isIndeterminate = indeterminate
    }
  }

  override fun cancel() {
    stop()
    super.cancel()
    SwingUtilities.invokeLater {
      progressBar.value = 0
      progressBar.isIndeterminate = false
    }
  }
}