package com.intellij.notebooks.images

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import org.jetbrains.concurrency.Promise
import org.jetbrains.plugins.notebooks.visualization.r.inlays.ClipboardUtils
import org.jetbrains.plugins.notebooks.visualization.r.inlays.InlayDimensions
import org.jetbrains.plugins.notebooks.visualization.r.inlays.components.*
import org.jetbrains.plugins.notebooks.visualization.r.inlays.runAsyncInlay
import java.io.File
import javax.swing.SwingUtilities

class InlayOutputImg(parent: Disposable, editor: Editor)
  : InlayOutput(parent, editor, loadActions(CopyImageToClipboardAction.ID, SaveOutputAction.ID)), InlayOutput.WithCopyImageToClipboard, InlayOutput.WithSaveAs {
  private val graphicsPanel = GraphicsPanel(project, parent).apply {
    isAdvancedMode = true
  }

  override val isFullWidth = false

  init {
    toolbarPane.dataComponent = graphicsPanel.component
  }

  override fun addToolbar() {
    super.addToolbar()
    graphicsPanel.overlayComponent = toolbarPane.toolbarComponent
  }

  override fun addData(data: String, type: String) {
    showImageAsync(data, type).onSuccess {
      SwingUtilities.invokeLater {
        val maxHeight = graphicsPanel.maximumSize?.height ?: 0
        val maxWidth = graphicsPanel.maximumSize?.width ?: 0
        val height = InlayDimensions.calculateInlayHeight(maxWidth, maxHeight, editor)
        onHeightCalculated?.invoke(height)
      }
    }
  }

  private fun showImageAsync(data: String, type: String): Promise<Unit> {
    return runAsyncInlay {
      when (type) {
        "IMGBase64" -> graphicsPanel.showImageBase64(data)
        "IMGSVG" -> graphicsPanel.showSvgImage(data)
        "IMG" -> graphicsPanel.showImage(File(data))
        else -> Unit
      }
    }
  }

  override fun clear() {
  }

  override fun scrollToTop() {
  }

  override fun getCollapsedDescription(): String {
    return "foo"
  }

  override fun saveAs() {
    graphicsPanel.image?.let { image ->
      InlayOutputUtil.saveImageWithFileChooser(project, image)
    }
  }

  override fun acceptType(type: String): Boolean {
    return type == "IMG" || type == "IMGBase64" || type == "IMGSVG"
  }

  override fun copyImageToClipboard() {
    graphicsPanel.image?.let { image ->
      ClipboardUtils.copyImageToClipboard(image)
    }
  }
}
