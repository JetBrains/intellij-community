// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.screenshots.builtInButton

import com.intellij.icons.AllIcons
import com.intellij.internal.ui.sandbox.UISandboxScreenshotPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.dsl.builder.COLUMNS_SHORT
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

/**
 * @author Konstantin Bulenkov
 */
internal class BuiltInButtonWhenToUseCorrectPanel : UISandboxScreenshotPanel() {
  override val title: String = "When To Use Correct"
  override val screenshotSize = 756 x 360
  override val sreenshotRelativePath = "images/ui/built_in_button/built_in_button_browse_correct.png"

  override fun createContentForScreenshot(disposable: Disposable): JComponent {
    return panel {
      row("File:") {
        textFieldWithBrowseButton()
          .columns(COLUMNS_SHORT)
      }
      row("File:") {
        cell (ExtendableTextField().apply {
          addExtension { hovered -> if (hovered) AllIcons.General.InlineVariablesHover else AllIcons.General.InlineVariables }
          isOpaque = false
        }).columns(COLUMNS_SHORT)
      }
    }
  }
}
