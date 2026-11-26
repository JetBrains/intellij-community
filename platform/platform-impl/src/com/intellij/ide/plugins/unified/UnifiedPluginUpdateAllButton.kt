// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.IdeBundle
import com.intellij.ui.AnimatedIcon
import org.jetbrains.annotations.Nls
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent

internal class UnifiedPluginUpdateAllButton(
  onUpdateRequested: () -> Unit,
) {
  private val button = JButton()
  private var presentation: UnifiedPluginUpdateAllPresentation = UnifiedPluginUpdateAllPresentation.Hidden

  val component: JComponent = button

  init {
    button.addActionListener {
      when (presentation) {
        UnifiedPluginUpdateAllPresentation.Available, UnifiedPluginUpdateAllPresentation.Failed ->
          onUpdateRequested()
        else -> Unit
      }
    }
    render(presentation)
  }

  fun render(presentation: UnifiedPluginUpdateAllPresentation) {
    this.presentation = presentation
    when (presentation) {
      UnifiedPluginUpdateAllPresentation.Hidden, UnifiedPluginUpdateAllPresentation.RestartRequired -> {
        button.isVisible = false
      }
      UnifiedPluginUpdateAllPresentation.Available, UnifiedPluginUpdateAllPresentation.Failed -> {
        show(
          text = IdeBundle.message("plugins.configurable.update.all.button"),
          icon = null,
          enabled = true,
        )
      }
      is UnifiedPluginUpdateAllPresentation.Running -> {
        show(
          text = IdeBundle.message("plugins.configurable.updating.button", presentation.prepared, presentation.total),
          icon = AnimatedIcon.Default.INSTANCE,
          enabled = false,
        )
      }
      UnifiedPluginUpdateAllPresentation.Updated -> {
        show(
          text = IdeBundle.message("plugins.configurable.updated.button"),
          icon = null,
          enabled = false,
        )
      }
    }
  }

  private fun show(text: @Nls String, icon: Icon?, enabled: Boolean) {
    button.text = text
    button.icon = icon
    button.isEnabled = enabled
    button.isVisible = true
  }
}
