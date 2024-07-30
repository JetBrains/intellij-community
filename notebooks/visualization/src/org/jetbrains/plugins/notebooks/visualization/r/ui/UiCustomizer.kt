package org.jetbrains.plugins.notebooks.visualization.r.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.plugins.notebooks.visualization.r.inlays.components.ToolbarPane
import org.jetbrains.plugins.notebooks.visualization.r.inlays.components.progress.InlayProgressStatus
import java.awt.Color
import javax.swing.JComponent
import javax.swing.JPanel

interface UiCustomizer {

  fun toolbarPaneToolbarComponentChanged(toolbarPane: ToolbarPane, component: JComponent?)

  fun toolbarPaneProgressComponentChanged(toolbarPane: ToolbarPane, component: JComponent?)

  fun toolbarPaneMainPanelCreated(toolbarPane: ToolbarPane, panel: JPanel?)

  fun getTextOutputBackground(editor: Editor): Color

  fun buildInlayProgressStatusComponent(progressStatus: InlayProgressStatus, editor: Editor): JComponent?

  val isResizeOutputToPreviewHeight: Boolean
    get() = true

  val showUpdateCellSeparator: Boolean

  companion object {
    val EP_NAME: ExtensionPointName<UiCustomizer> = ExtensionPointName("org.jetbrains.plugins.notebooks.visualization.r.inlays.visualisation.uiCustomizer")

    val instance: UiCustomizer get() = EP_NAME.extensionList.first()
  }
}
