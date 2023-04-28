// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.ui.representation

import com.intellij.ide.customize.transferSettings.ui.representation.ideVersion.TransferSettingsIdeRepresentationListener
import javax.swing.JComponent

interface TransferSettingsRepresentationPanel {
  fun getComponent(): JComponent
  fun block()
  fun onStateChange(action: TransferSettingsIdeRepresentationListener)
}