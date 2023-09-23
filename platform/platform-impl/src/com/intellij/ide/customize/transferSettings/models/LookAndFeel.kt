// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.models

import com.intellij.ide.customize.transferSettings.TransferableLafId
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfo
import com.intellij.ide.ui.laf.UiThemeProviderListManager

interface ILookAndFeel {
  val transferableId: TransferableLafId
  fun getPreview(): UIThemeLookAndFeelInfo
}

class BundledLookAndFeel(override val transferableId: TransferableLafId, val lafInfo: UIThemeLookAndFeelInfo): ILookAndFeel {
  companion object {
    fun fromManager(transferableId: TransferableLafId, lafName: String): BundledLookAndFeel = UiThemeProviderListManager.getInstance().findThemeByName(lafName)
      ?.let { BundledLookAndFeel(transferableId, it) } ?: error("LookAndFeel $lafName not found")
  }

  override fun getPreview() = lafInfo
}

class PluginLookAndFeel(
  override val transferableId: TransferableLafId,
  val pluginId: String,
  val installedName: String,
  val fallback: BundledLookAndFeel
) : ILookAndFeel {
  override fun getPreview() = fallback.lafInfo
}

class SystemDarkThemeDetectorLookAndFeel(private val darkLaf: ILookAndFeel, val lightLaf: ILookAndFeel) : ILookAndFeel {
  override fun getPreview() = darkLaf.getPreview() // TODO return
  override val transferableId = darkLaf.transferableId
}