// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.JBColor
import com.intellij.ui.MouseDragHelper
import com.intellij.ui.PopupBorder
import com.intellij.ui.WindowRoundedCornersManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ImageLoader
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Image
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JRootPane
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.border.Border

/**
 * @author Alexander Lobas
 */
@Internal
abstract class LicenseExpirationDialog(project: Project?, private val imagePath: String) :
  DialogWrapper(project, null, false, IdeModalityType.IDE, false) {

  protected fun initDialog(@NlsContexts.DialogTitle title: String) {
    this.title = title
    setInitialLocationCallback {
      val rootPane: JRootPane? = SwingUtilities.getRootPane(window.parent) ?: SwingUtilities.getRootPane(window.owner)
      if (rootPane == null || !rootPane.isShowing) {
        return@setInitialLocationCallback null
      }
      val location = rootPane.locationOnScreen
      Point(location.x + (rootPane.width - window.width) / 2, (location.y + rootPane.height * 0.25).toInt())
    }

    setUndecorated(true)
    init()

    val pane = contentPane as JComponent
    pane.isOpaque = true
    pane.background = JBColor.white
    UIUtil.uiChildren(pane).forEach { (it as JComponent).isOpaque = false }

    rootPane.windowDecorationStyle = JRootPane.NONE
    rootPane.border = PopupBorder.Factory.create(true, true)

    object : MouseDragHelper<JComponent>(myDisposable, pane) {
      var myLocation: Point? = null

      override fun canStartDragging(dragComponent: JComponent, dragComponentPoint: Point): Boolean {
        val target = dragComponent.findComponentAt(dragComponentPoint)
        return target == null || target == dragComponent || target is JPanel || target is JBLabel
      }

      override fun processDrag(event: MouseEvent, dragToScreenPoint: Point, startScreenPoint: Point) {
        if (myLocation == null) {
          myLocation = window.location
        }
        window.location = Point(myLocation!!.x + dragToScreenPoint.x - startScreenPoint.x,
                                myLocation!!.y + dragToScreenPoint.y - startScreenPoint.y)
      }

      override fun processDragCancel() {
        myLocation = null
      }

      override fun processDragFinish(event: MouseEvent, willDragOutStart: Boolean) {
        myLocation = null
      }

      override fun processDragOutFinish(event: MouseEvent) {
        myLocation = null
      }

      override fun processDragOutCancel() {
        myLocation = null
      }

      override fun processDragOut(event: MouseEvent, dragToScreenPoint: Point, startScreenPoint: Point, justStarted: Boolean) {
        super.processDragOut(event, dragToScreenPoint, startScreenPoint, justStarted)
        myLocation = null
      }
    }.start()

    WindowRoundedCornersManager.configure(this)
  }

  override fun createContentPaneBorder(): Border? = JBUI.Borders.empty()

  override fun createCenterPanel(): JComponent {
    val panel = JPanel(BorderLayout())
    panel.isOpaque = false

    panel.add(createAndConfigurePanel())

    val buttons = JPanel(HorizontalLayout(17, SwingConstants.CENTER))
    buttons.isOpaque = false
    buttons.border = JBUI.Borders.empty(0, 32, 16, 32)

    updateOKActionText()
    buttons.add(createJButtonForAction(myOKAction).also { it.isOpaque = false })

    val listener = LinkListener<Any> { _, _ -> doCancelAction() }
    buttons.add(LinkLabel(getCancelActionText(), null, listener))

    panel.add(buttons, BorderLayout.SOUTH)

    val label = object : JBLabel() {
      val image = loadImage()!!
      var scaleImage: Image? = null
      var imageWidth = 0
      var imageHeight = 0

      override fun paintComponent(g: Graphics) {
        val newWidth = width
        val newHeight = height

        if (scaleImage == null || imageWidth != newWidth || imageHeight != newHeight) {
          imageWidth = newWidth
          imageHeight = newHeight
          scaleImage = ImageLoader.scaleImage(image, newWidth, newHeight)
        }
        StartupUiUtil.drawImage(g, scaleImage!!, Rectangle(0, 0, newWidth, newHeight), this)
      }
    }
    configureHeader(label)

    val contentWidth = panel.preferredSize.width
    val contentSize = Dimension(contentWidth, (contentWidth / 1.8).toInt())
    label.isOpaque = false
    label.preferredSize = contentSize

    panel.add(label, BorderLayout.NORTH)

    return panel
  }

  protected open fun configureHeader(header: JComponent) {
  }

  protected open fun createAndConfigurePanel(): JComponent {
    val panel = createPanel()

    panel.isOpaque = false
    panel.border = JBUI.Borders.empty(28, 32, 16, 32)

    return panel
  }

  protected abstract fun createPanel(): JComponent

  protected fun updateOKActionText() {
    myOKAction.putValue(Action.NAME, getOKActionText())
  }

  protected abstract fun getOKActionText(): @Nls String

  protected abstract fun getCancelActionText(): @Nls String

  private fun loadImage(): Image? {
    try {
      return ImageLoader.loadFromStream(javaClass.getResourceAsStream(imagePath)!!)
    }
    catch (e: Exception) {
      logger<InProductNotificationDialog>().error("Image $imagePath is not loaded: $e")
      return null
    }
  }
}