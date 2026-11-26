// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.IdeBundle
import org.jetbrains.annotations.Nls
import javax.swing.JButton
import javax.swing.JComponent

internal class UnifiedPluginUpdateAllButton(
  onUpdateRequested: () -> Unit,
) {
  private val button = JButton().apply {
    addActionListener { onUpdateRequested() }
  }

  val component: JComponent = button

  init {
    render(UnifiedPluginUpdateAllPresentation.Hidden)
  }

  fun render(presentation: UnifiedPluginUpdateAllPresentation) {
    when (presentation) {
      UnifiedPluginUpdateAllPresentation.Available, UnifiedPluginUpdateAllPresentation.Failed ->
        show(IdeBundle.message("plugins.configurable.update.all.button"), enabled = true)
      UnifiedPluginUpdateAllPresentation.Updated ->
        show(IdeBundle.message("plugins.configurable.updated.button"), enabled = false)
      UnifiedPluginUpdateAllPresentation.Hidden, UnifiedPluginUpdateAllPresentation.RestartRequired,
      is UnifiedPluginUpdateAllPresentation.Running -> {
        button.isEnabled = false
        button.isVisible = false
      }
    }
  }

  private fun show(text: @Nls String, enabled: Boolean) {
    button.text = text
    button.isEnabled = enabled
    button.isVisible = true
  }
}
