package org.jetbrains.plugins.notebooks.visualization.r.inlays.components.progress

import com.intellij.ide.ui.laf.darcula.ui.DarculaProgressBarUI
import com.intellij.ui.JBColor
import java.awt.Color
import javax.swing.JComponent

class InlayProgressBarUI(private val status: ProgressStatus) : DarculaProgressBarUI() {
  override fun getFinishedColor(c: JComponent): Color = if (status == ProgressStatus.STOPPED_OK) {
    JBColor.GRAY
  }
  else {
    JBColor.RED
  }
}