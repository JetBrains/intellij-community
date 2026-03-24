// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.screenshots.checkbox

import com.intellij.internal.ui.sandbox.UISandboxScreenshotPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent
import javax.swing.SwingConstants

/**
 * @author Konstantin Bulenkov
 */
internal class LabelOnTheRightIncorrectPanel: UISandboxScreenshotPanel() {
  override val title: String = "Incorrect"
  override val screenshotSize = 756 x 256
  override val sreenshotRelativePath = "images/ui/checkbox/checkbox_label_right_incorrect.png"

  override fun createContentForScreenshot(disposable: Disposable): JComponent {
    return panel {
      row { checkBox("Use secure connection").apply {
        component.isSelected = true
        component.horizontalTextPosition = SwingConstants.LEFT
      } }
    }
  }
}