// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.screenshots.checkbox

import com.intellij.internal.ui.sandbox.UISandboxScreenshotPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

/**
 * @author Konstantin Bulenkov
 */
internal class WhenToUseCheckboxesPanel : UISandboxScreenshotPanel() {
  override val title: String = "When to use"
  override val screenshotSize = null
  override val sreenshotRelativePath = null

  override fun createContentForScreenshot(disposable: Disposable): JComponent {
    return panel {
      @Suppress("DialogTitleCapitalization")
      buttonsGroup("UI Options:") {
        row { checkBox("Smooth scrolling").apply { component.isSelected = true } }
        row { checkBox("Display icons in menu items").apply { component.isSelected = true } }
        row { checkBox("Enable mnemonics in menu") }
      }
    }
  }
}