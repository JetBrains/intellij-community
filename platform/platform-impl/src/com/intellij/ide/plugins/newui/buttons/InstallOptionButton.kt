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
) : OptionButton(action, options), PluginInstallButton {
  private val isUpgradeRequired = isUpgradeRequired

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
      setWidth72(this)
    }
  }

  override fun setButtonColors(fill: Boolean) {
  }

  override fun setEnabled(enabled: Boolean, statusText: @Nls String?) {
    isEnabled = enabled
    if (enabled) {
      setTextAndSize(statusText)
    }
    else {
      text = statusText
      setWidth(this, 80)
    }
  }

  override fun setAction(a: Action?) {
    super.setAction(a)
    setWidth(this, 80)
  }

  override fun setEnabled(b: Boolean) {
    super.setEnabled(b)
    action?.isEnabled = b
  }

  override fun getComponent(): JComponent = this
}
