// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.util.concurrency.annotations.RequiresEdt
import javax.swing.JComponent

internal enum class PluginDetailsMode {
  LOCAL,
  MARKETPLACE,
}

internal data class PluginDetailsSelection(
  val occurrenceId: PluginOccurrenceId,
  val row: PluginRow,
)

internal interface PluginDetailsPresenter : AutoCloseable {
  val component: JComponent

  @RequiresEdt
  fun render(mode: PluginDetailsMode, selection: List<PluginDetailsSelection>)

  @RequiresEdt
  fun beforeRowRelease(occurrenceId: PluginOccurrenceId, row: PluginRow)

  @RequiresEdt
  override fun close()
}

internal fun pluginDetailsMode(sectionId: PluginSectionId): PluginDetailsMode {
  return when (sectionId) {
    PluginSectionId.Installing, PluginSectionId.Installed, PluginSectionId.Bundled -> PluginDetailsMode.LOCAL
    PluginSectionId.Internal, PluginSectionId.Suggested, PluginSectionId.Marketplace,
    PluginSectionId.CustomRepositoryCatalog, is PluginSectionId.CustomRepository -> PluginDetailsMode.MARKETPLACE
  }
}
