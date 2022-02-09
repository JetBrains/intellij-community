/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.components

import com.intellij.ui.components.ActionLink
import org.jetbrains.plugins.notebooks.visualization.r.VisualizationBundle
import java.awt.FlowLayout
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Shown on empty inlay with text like "Press run button to get result."
 * Or in collapsed inlay state with short description and possible some buttons on the right, like "Clear output".
 * Toolbar background is dark on Darkula and white on other themes.
 */
class NotebookInlayToolbar : JPanel(FlowLayout(FlowLayout.LEFT)) {
  fun setDefaultState(runAction: (() -> Unit)) {
    removeAll()
    add(ActionLink(VisualizationBundle.message("notebook.inlay.run.cell")) { runAction.invoke() })
    add(JLabel(VisualizationBundle.message("notebook.inlay.to.see.results")))
    repaint()
  }
}