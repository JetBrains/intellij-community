// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.models

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfo

interface ILookAndFeel {
  fun getPreview(): UIThemeLookAndFeelInfo
}

class BundledLookAndFeel(val lafInfo: UIThemeLookAndFeelInfo): ILookAndFeel {
  companion object {
    fun fromManager(lafName: String): BundledLookAndFeel = LafManager.getInstance().installedLookAndFeels
      .map { it as? UIThemeLookAndFeelInfo }.first { it?.name == lafName }
      ?.let { BundledLookAndFeel(it) } ?: error("LookAndFeel $lafName not found")
  }

  override fun getPreview() = lafInfo
}

class PluginLookAndFeel(val pluginId: String, val installedName: String, val fallback: BundledLookAndFeel) : ILookAndFeel {
  override fun getPreview() = fallback.lafInfo
}

class SystemDarkThemeDetectorLookAndFeel(private val darkLaf: ILookAndFeel, val lightLaf: ILookAndFeel) : ILookAndFeel {
  override fun getPreview() = darkLaf.getPreview() // TODO return
}