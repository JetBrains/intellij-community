package org.jetbrains.plugins.notebooks.visualization.r.ui

import com.intellij.openapi.editor.Editor
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.notebooks.visualization.r.inlays.components.progress.InlayProgressStatus
import org.jetbrains.plugins.notebooks.visualization.r.inlays.components.progress.JupyterProgressStatus
import javax.swing.JComponent

class DefaultUiCustomizer : UiCustomizer {
  override fun getTextOutputBackground(editor: Editor) = UIUtil.getPanelBackground()

  override fun buildInlayProgressStatusComponent(progressStatus: InlayProgressStatus, editor: Editor): JComponent? {
    return JupyterProgressStatus.buildProgressStatusComponent(progressStatus, editor)
  }

  override val showUpdateCellSeparator: Boolean = true
}
