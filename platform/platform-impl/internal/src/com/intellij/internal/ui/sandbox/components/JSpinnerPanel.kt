// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.components

import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.internal.ui.sandbox.withStateLabel
import com.intellij.openapi.Disposable
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

internal class JSpinnerPanel : UISandboxPanel {

  override val title: String = "JSpinner"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      withStateLabel {
        spinner(0.0..10.0)
      }
      withStateLabel {
        spinner(0.0..10.0)
          .enabled(false)
      }
    }
  }
}