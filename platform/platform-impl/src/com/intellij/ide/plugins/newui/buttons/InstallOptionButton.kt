// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui.buttons

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.newui.ColorButton.setWidth
import com.intellij.ide.plugins.newui.ColorButton.setWidth72
import com.intellij.ide.plugins.newui.PluginInstallButton
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Action
import javax.swing.JComponent

@ApiStatus.Internal
class InstallOptionButton @JvmOverloads constructor(
  isUpgradeRequired: Boolean = false,
  action: Action? = null,
  options: Array<Action>? = null,
  useNaturalWidth: Boolean = false,
) : OptionButton(action, options), PluginInstallButton {
  private val isUpgradeRequired = isUpgradeRequired
  private var useNaturalWidth = false

  init {
    // JButton(Action) may call the overridden setAction before this class is initialized.
    // Keep the legacy width during superclass construction, then apply the requested mode.
    this.useNaturalWidth = useNaturalWidth
    applyActionWidth()
  }

  override fun updateUI() {
    super.updateUI()
    if (parent != null) {
      setEnabled(isEnabled, text)
    }
  }


  fun setTextAndSize(statusText: @Nls String?) {
    text = statusText ?: IdeBundle.message("action.AnActionButton.text.install")
    isEnabled = !isUpgradeRequired
    if (statusText != null) {
      setWidth(this, 80)
    }
    else {
      applyInstallTextWidth()
    }
  }

  override fun setButtonColors(fill: Boolean) {
  }

  override fun setEnabled(enabled: Boolean, statusText: @Nls String?) {
    isEnabled = enabled
    if (enabled) {
      setTextAndSize(null)
    }
    else {
      text = statusText
      setWidth(this, 80)
    }
  }

  override fun setAction(a: Action?) {
    super.setAction(a)
    applyActionWidth()
  }

  override fun setEnabled(b: Boolean) {
    super.setEnabled(b)
    action?.isEnabled = b
  }

  override fun getComponent(): JComponent = this

  private fun applyActionWidth() {
    if (useNaturalWidth) {
      preferredSize = null
    }
    else {
      setWidth(this, 80)
    }
  }

  private fun applyInstallTextWidth() {
    if (useNaturalWidth) {
      preferredSize = null
    }
    else {
      setWidth72(this)
    }
  }
}
