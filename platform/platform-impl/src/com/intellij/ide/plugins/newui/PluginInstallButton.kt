// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

@ApiStatus.Internal
interface PluginInstallButton {

  fun setButtonColors(fill: Boolean)

  fun setEnabled(enabled: Boolean, statusText: @Nls String?)

  fun setVisible(visible: Boolean)

  fun isVisible(): Boolean

  fun getComponent(): JComponent
}