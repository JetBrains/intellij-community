// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.editor.impl.jcef

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.Magnificator
import com.intellij.ui.components.ZoomableViewport
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ObjectUtils
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.intellij.images.ImagesBundle
import org.intellij.images.editor.actionSystem.ImageEditorActions
import org.intellij.images.editor.impl.jcef.JCefImageViewer.Companion.isDebugMode
import org.intellij.images.options.OptionsManager
import org.intellij.images.ui.ImageComponentDecorator
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.Point
import java.awt.event.MouseWheelListener
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

class JCefImageViewerUI(private val myContentComponent: Component,
                        private val myViewer: JCefImageViewer
) : JPanel(), UiDataProvider, Disposable {
  private val myInfoLabel: JLabel
  private val myViewPort: JPanel

  override fun uiDataSnapshot(sink: DataSink) {
    sink[ImageComponentDecorator.DATA_KEY] = myViewer
  }

  override fun dispose() {
    myViewer.preferredFocusedComponent.removeMouseWheelListener(MOUSE_WHEEL_LISTENER)
  }

  fun setInfo(info: @Nls String) {
    myInfoLabel.text = info
  }

  private inner class ViewPort : JPanel(BorderLayout()), ZoomableViewport {
    private var myMagnificationPoint: Point? = null
    private var myOriginalZoom = 1.0
    private fun convertToContentCoordinates(point: Point): Point {
      return SwingUtilities.convertPoint(this, point, myContentComponent)
    }

    override fun getMagnificator(): Magnificator {
      return Magnificator { scale, at ->
        myViewer.setZoom(scale, at)
        at
      }
    }

    override fun magnificationStarted(at: Point) {
      myMagnificationPoint = at
      myOriginalZoom = myViewer.getZoom()
    }

    override fun magnificationFinished(magnification: Double) {
      myMagnificationPoint = null
      myOriginalZoom = 1.0
    }

    override fun magnify(magnification: Double) {
      val p = myMagnificationPoint
      if (magnification.compareTo(0.0) != 0 && p != null) {
        val magnificator = magnificator
        val inContentPoint = convertToContentCoordinates(p)
        val scale = if (magnification < 0) 1f / (1 - magnification) else 1 + magnification
        magnificator.magnify(myOriginalZoom * scale, inContentPoint)
      }
    }
  }

  private val MOUSE_WHEEL_LISTENER = MouseWheelListener { e ->
    val zoomOptions = OptionsManager.getInstance().options.editorOptions.zoomOptions
    if (zoomOptions.isWheelZooming && e.isControlDown) {
      val rotation = e.wheelRotation
      if (rotation < 0) {
        myViewer.setZoom(myViewer.getZoom() * 1.2, Point(e.x, e.y))
      }
      else if (rotation > 0) {
        myViewer.setZoom(myViewer.getZoom() / 1.2, Point(e.x, e.y))
      }
      e.consume()
    }
  }

  init {
    layout = BorderLayout()
    val actionManager = ActionManager.getInstance()
    val actionGroup = actionManager.getAction(ImageEditorActions.GROUP_TOOLBAR) as ActionGroup
    val actionToolbar = actionManager.createActionToolbar(ImageEditorActions.ACTION_PLACE, actionGroup, true)
    actionToolbar.targetComponent = this

    background = JBColor.lazy {
      ObjectUtils.notNull(
        EditorColorsManager.getInstance().globalScheme.getColor(EditorColors.PREVIEW_BACKGROUND),
        EditorColorsManager.getInstance().globalScheme.defaultBackground)
    }

    val toolbarPanel = actionToolbar.component
    toolbarPanel.background = JBColor.lazy { background ?: UIUtil.getPanelBackground()}
    val topPanel: JPanel = NonOpaquePanel(BorderLayout())
    topPanel.add(toolbarPanel, BorderLayout.WEST)

    myInfoLabel = JLabel(null as String?, SwingConstants.RIGHT)
    myInfoLabel.border = JBUI.Borders.emptyRight(2)
    topPanel.add(myInfoLabel, BorderLayout.EAST)
    add(topPanel, BorderLayout.NORTH)

    myViewPort = ViewPort()
    myViewPort.setLayout(CardLayout())
    myViewer.preferredFocusedComponent.addMouseWheelListener(MOUSE_WHEEL_LISTENER)
    myViewPort.add(myContentComponent, IMAGE_PANEL)
    myContentComponent.background = JBColor.lazy { background ?: UIUtil.getPanelBackground()}

    val errorLabel = JLabel(
      ImagesBundle.message("error.broken.image.file.format"),
      Messages.getErrorIcon(), SwingConstants.CENTER
    )
    val errorPanel = JPanel(BorderLayout())
    errorPanel.add(errorLabel, BorderLayout.CENTER)

    myViewPort.add(myContentComponent, IMAGE_PANEL)
    myViewPort.add(errorPanel, ERROR_PANEL)
    add(myViewPort, BorderLayout.CENTER)

    if (!isDebugMode()) { // Use the context menu for calling devtools in debug mode
      PopupHandler.installPopupMenu(myViewer.preferredFocusedComponent, ImageEditorActions.GROUP_POPUP, ImageEditorActions.ACTION_PLACE)
    }
  }

  fun showError() = (myViewPort.layout as CardLayout).show(myViewPort, ERROR_PANEL)
  fun showImage() = (myViewPort.layout as CardLayout).show(myViewPort, IMAGE_PANEL)

  companion object {
    private const val IMAGE_PANEL: @NonNls String = "image"
    private const val ERROR_PANEL: @NonNls String = "error"
  }
}