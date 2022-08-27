// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.models

import com.intellij.ide.ui.LafManager
import org.jetbrains.annotations.Nls
import javax.swing.UIManager

interface ILookAndFeel {
  val displayName: @Nls String
}

class BundledLookAndFeel(override val displayName: @Nls String, val lafInfo: UIManager.LookAndFeelInfo): ILookAndFeel {
  companion object {
    fun fromManager(lafName: String) = requireNotNull(LafManager.getInstance().installedLookAndFeels.first { it.name == lafName }
                                                        ?.let { BundledLookAndFeel(it.name, it) })
  }
}

class PluginLookAndFeel(override val displayName: @Nls String, val pluginId: String, val installedName: String, val fallback: BundledLookAndFeel) : ILookAndFeel

class SystemDarkThemeDetectorLookAndFeel(val darkLaf: ILookAndFeel, val lightLaf: ILookAndFeel) : ILookAndFeel {
  override val displayName = "Sync with OS"
}