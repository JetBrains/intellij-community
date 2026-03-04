// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.screenshots.builtInButton

import com.intellij.internal.ui.sandbox.UISandboxScreenshotPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

/**
 * @author Konstantin Bulenkov
 */
internal class BuiltInButtonListValuesPanel : UISandboxScreenshotPanel() {
  override val title: String = "List Values"
  override val screenshotSize = 1412 x 360
  override val sreenshotRelativePath = null //"images/ui/built_in_button/built_in_button_list.png"

  override fun createContentForScreenshot(disposable: Disposable): JComponent {
    return panel {
      row("TODO") {

      }
    }
  }
}