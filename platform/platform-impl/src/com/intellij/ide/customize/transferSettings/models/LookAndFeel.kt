// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.models

import com.intellij.ide.ui.LafManager
import javax.swing.UIManager

interface ILookAndFeel {
  fun getPreview(): UIManager.LookAndFeelInfo
}

class BundledLookAndFeel(val lafInfo: UIManager.LookAndFeelInfo): ILookAndFeel {
  companion object {
    fun fromManager(lafName: String): BundledLookAndFeel = LafManager.getInstance().installedLookAndFeels.first { it.name == lafName }
      ?.let { BundledLookAndFeel(it) } ?: error("LookAndFeel $lafName not found")
  }

  override fun getPreview() = lafInfo
}

class PluginLookAndFeel(val pluginId: String, val installedName: String, val fallback: BundledLookAndFeel) : ILookAndFeel {
  override fun getPreview() = fallback.lafInfo
}

class SystemDarkThemeDetectorLookAndFeel(val darkLaf: ILookAndFeel, val lightLaf: ILookAndFeel) : ILookAndFeel {
  override fun getPreview() = darkLaf.getPreview() // TODO return
}