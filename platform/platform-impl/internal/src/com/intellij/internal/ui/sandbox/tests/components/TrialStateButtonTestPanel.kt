// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.tests.components

import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.trialState.TrialStateButton
import com.intellij.ui.components.trialState.TrialStateButton.ColorState
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

internal class TrialStateButtonTestPanel : UISandboxPanel {

  override val title: String = "TrialStateButton"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      row {
        trialStateButton("30-Day Trial Started", ColorState.ACTIVE)
        trialStateButton("7 Days of Trial Left", ColorState.ALERT)
        trialStateButton("1 Day of Trial Left", ColorState.EXPIRING)
        trialStateButton("Default", ColorState.DEFAULT)
      }
    }
  }

  private fun Row.trialStateButton(@NlsContexts.Button text: String, colorState: ColorState) {
    cell(TrialStateButton()).applyToComponent {
      this.text = text
      setColorState(colorState)
    }
  }
}