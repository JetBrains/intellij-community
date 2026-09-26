// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ui.AnimatedIcon
import org.jetbrains.annotations.Nls
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent

internal sealed interface UnifiedPluginUpdateAllIntent {
  data object Update : UnifiedPluginUpdateAllIntent

  data object Restart : UnifiedPluginUpdateAllIntent
}

internal class UnifiedPluginUpdateAllButton(
  private val onIntent: (UnifiedPluginUpdateAllIntent) -> Unit,
) {
  private val button = JButton()
  private var presentation: UnifiedPluginUpdateAllPresentation = UnifiedPluginUpdateAllPresentation.Hidden

  val component: JComponent = button

  init {
    button.addActionListener {
      when (presentation) {
        UnifiedPluginUpdateAllPresentation.Available, UnifiedPluginUpdateAllPresentation.Failed ->
          onIntent(UnifiedPluginUpdateAllIntent.Update)
        UnifiedPluginUpdateAllPresentation.RestartRequired -> onIntent(UnifiedPluginUpdateAllIntent.Restart)
        else -> Unit
      }
    }
    render(presentation)
  }

  fun render(presentation: UnifiedPluginUpdateAllPresentation) {
    this.presentation = presentation
    when (presentation) {
      UnifiedPluginUpdateAllPresentation.Hidden -> {
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
      UnifiedPluginUpdateAllPresentation.RestartRequired -> {
        show(
          text = IdeBundle.message("plugins.configurable.restart.ide.button"),
          icon = AllIcons.Actions.Restart,
          enabled = true,
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
