// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.screenshots.builtInButton

import com.intellij.internal.ui.sandbox.UISandboxScreenshotPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.dsl.builder.panel
import java.awt.Dimension
import javax.swing.JComponent

/**
 * @author Konstantin Bulenkov
 */
internal class BuiltInButtonCopyPasteIncorrectPanel : UISandboxScreenshotPanel() {
  override val title: String = "Copy Paste Incorrect"
  override val screenshotSize: Dimension? = null
  override val sreenshotRelativePath: String? = null

  override fun createContentForScreenshot(disposable: Disposable): JComponent {
    return panel {
      row("TODO") {}
    }
  }
}