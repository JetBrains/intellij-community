// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.history.Label
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class VersionPanel(private val versionLabel: JBLabel) : NonOpaquePanel(HorizontalLayout(JBUI.scale(8))) {
  override fun getBaseline(width: Int, height: Int): Int {
    // Return the baseline of the version label component, using preferred size if not yet laid out
    val labelSize = versionLabel.preferredSize
    return versionLabel.getBaseline(labelSize.width, labelSize.height)
  }

}