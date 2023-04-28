// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.ui.representation.ideVersion.sections

import com.intellij.ide.customize.transferSettings.models.SettingsPreferencesKind
import com.intellij.ide.customize.transferSettings.ui.representation.ideVersion.TransferSettingsIdeRepresentationListener
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

interface TransferSettingsSection {
  val key: SettingsPreferencesKind
  val name: @Nls String

  fun worthShowing(): Boolean
  fun block()
  fun getUI(): JComponent

  fun onStateUpdate(action: TransferSettingsIdeRepresentationListener)
}