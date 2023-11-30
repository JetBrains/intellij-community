// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.preview

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.ui.LoadingDecorator
import com.intellij.ui.ColorUtil
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.OpaquePanel
import com.intellij.util.ui.AnimatedIcon
import com.intellij.util.ui.AsyncProcessIcon
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import javax.swing.JLabel
import javax.swing.JPanel

internal class IntentionPreviewLoadingDecorator(panel: JPanel, parent: Disposable) :
  LoadingDecorator(panel, parent, 500, false, AsyncProcessIcon("IntentionPreviewProcessLoading")) {
  override fun customizeLoadingLayer(parent: JPanel, text: JLabel, icon: AnimatedIcon): NonOpaquePanel {
    val editorBackground = EditorColorsManager.getInstance().globalScheme.defaultBackground
    val iconNonOpaquePanel = OpaquePanel(FlowLayout(FlowLayout.RIGHT, 2, 2))
      .apply {
        add(icon, BorderLayout.NORTH)
        background = editorBackground
      }

    icon.background = editorBackground.withAlpha(0.0)
    icon.isOpaque = true

    val opaquePanel = OpaquePanel()
    opaquePanel.background = editorBackground.withAlpha(0.6)

    val nonOpaquePanel = NonOpaquePanel(BorderLayout())
    nonOpaquePanel.add(iconNonOpaquePanel, BorderLayout.EAST)
    nonOpaquePanel.add(opaquePanel, BorderLayout.CENTER)

    parent.layout = BorderLayout()
    parent.add(nonOpaquePanel)

    return nonOpaquePanel
  }

  fun Color.withAlpha(alpha: Double): Color = ColorUtil.withAlpha(this, alpha)
}