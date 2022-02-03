package org.jetbrains.plugins.notebooks.visualization.r.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.intellij.images.editor.ImageEditor
import org.jetbrains.plugins.notebooks.visualization.r.inlays.components.GraphicsPanel
import org.jetbrains.plugins.notebooks.visualization.r.inlays.components.InlayProgressStatus
import org.jetbrains.plugins.notebooks.visualization.r.inlays.components.ToolbarPane
import java.awt.Color
import javax.swing.JComponent
import javax.swing.JPanel

interface UiCustomizer {
  fun createImageEditor(project: Project,
                        file: VirtualFile,
                        graphicsPanel: GraphicsPanel): ImageEditor

  fun toolbarPaneToolbarComponentChanged(toolbarPane: ToolbarPane, component: JComponent?)

  fun toolbarPaneProgressComponentChanged(toolbarPane: ToolbarPane, component: JComponent?)

  fun toolbarPaneMainPanelCreated(toolbarPane: ToolbarPane, panel: JPanel?)

  fun getTextOutputBackground(editor: Editor): Color

  fun buildInlayProgressStatusComponent(progressStatus: InlayProgressStatus, editor: Editor): JComponent?

  @JvmDefault
  val isResizeOutputToPreviewHeight: Boolean
    get() = true

  val showUpdateCellSeparator: Boolean

  companion object {
    val EP_NAME: ExtensionPointName<UiCustomizer> = ExtensionPointName("org.jetbrains.plugins.notebooks.visualization.r.inlays.visualisation.uiCustomizer")

    val instance: UiCustomizer get() = EP_NAME.extensionList.first()
  }
}