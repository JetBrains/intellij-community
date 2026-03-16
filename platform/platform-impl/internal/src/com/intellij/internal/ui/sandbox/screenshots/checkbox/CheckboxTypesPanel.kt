// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.screenshots.checkbox

import com.intellij.internal.ui.sandbox.UISandboxScreenshotPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.ThreeStateCheckBox
import javax.swing.JComponent

/**
 * @author Konstantin Bulenkov
 */
internal class CheckboxTypesPanel : UISandboxScreenshotPanel() {
  override val title: String = "Checkbox types"
  override val screenshotSize = null
  override val sreenshotRelativePath = null

  override fun createContentForScreenshot(disposable: Disposable): JComponent {
    return panel {
      row {
        checkBox("Checked").apply { component.isSelected = true }
        cell(ThreeStateCheckBox("Indeterminate", ThreeStateCheckBox.State.DONT_CARE))
          .applyToComponent { isOpaque = false }
        checkBox("Unchecked")
        checkBox("Disabled").apply { component.isEnabled = false }
      }
    }
  }
}