// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox

import com.intellij.openapi.Disposable
import com.intellij.ui.JBColor
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import java.awt.Color
import java.awt.Dimension
import javax.swing.JComponent

/**
 * @author Konstantin Bulenkov
 */
private val SCREENSHOT_BACKGROUND: Color = JBColor(0xF7F8FA, 0x2B2D30)

internal abstract class UISandboxScreenshotPanel: UISandboxPanel {
  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      row {
        cell(createContentForScreenshot(disposable).apply { isOpaque = false })
          .align(Align.CENTER)
      }.resizableRow()
    }.apply {
      background = SCREENSHOT_BACKGROUND
    }
  }

  abstract fun createContentForScreenshot(disposable: Disposable): JComponent

  abstract val screenshotSize: Dimension?
  abstract val sreenshotRelativePath: String?
  
  infix fun Int.x(h: Int): Dimension = Dimension(this, h)
}