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
import com.intellij.ui.icons.HiDPIImage
import com.intellij.util.ui.JBImageIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Image
import java.awt.Point
import java.awt.event.MouseEvent
import javax.imageio.ImageIO
import javax.swing.*

/**
 * @author Alexander Lobas
 */
@Internal
abstract class LicenseExpirationDialog(
  project: Project?,
  private val imagePath: String,
  private val imageWidth: Int,
  private val imageHeight: Int,
) : DialogWrapper(project, null, false, IdeModalityType.IDE, false) {

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

    init()

    val pane = contentPane as JComponent
    pane.border = null
    pane.isOpaque = true
    pane.background = JBColor.white
    UIUtil.uiChildren(pane).forEach { (it as JComponent).isOpaque = false }

    setUndecorated(true)
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

  override fun createCenterPanel(): JComponent {
    val panel = JPanel(BorderLayout())
    panel.isOpaque = false

    val label = JBLabel(JBImageIcon(loadImage()!!))
    panel.add(label, BorderLayout.NORTH)

    panel.add(createAndConfigurePanel())

    val buttons = JPanel(HorizontalLayout(17, SwingConstants.CENTER))
    buttons.isOpaque = false
    buttons.border = JBUI.Borders.empty(0, 32, 16, 32)

    updateOKActionText()
    buttons.add(createJButtonForAction(myOKAction).also { it.isOpaque = false })

    val listener = LinkListener<Any> { aSource, aLinkData -> doCancelAction() }
    buttons.add(LinkLabel(getCancelActionText(), null, listener))

    panel.add(buttons, BorderLayout.SOUTH)

    return panel
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
      val image = ImageIO.read(javaClass.getResourceAsStream(imagePath))
      val width = JBUI.scale(imageWidth).coerceAtLeast(imageWidth)
      val height = JBUI.scale(imageHeight).coerceAtLeast(imageHeight)

      return HiDPIImage(image, width, height, image.type)
    }
    catch (e: Exception) {
      logger<InProductNotificationDialog>().error("Image $imagePath is not loaded: $e")
      return null
    }
  }
}