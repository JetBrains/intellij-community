// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.findIconUsingNewImplementation
import com.intellij.ui.Gray
import com.intellij.ui.RoundedIcon
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.paint.RectanglePainter
import com.intellij.util.IconUtil
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.*
import java.awt.BorderLayout.*
import java.awt.event.ActionListener
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import javax.swing.*
import javax.swing.event.ChangeListener
import kotlin.math.min

internal class RoundedIconTestAction : DumbAwareAction("Show Rounded Icon") {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    object : DialogWrapper(e.project, null, true, IdeModalityType.IDE, false) {
      private val label = JBLabel("Some text").apply {
        horizontalAlignment = SwingConstants.CENTER
        horizontalTextPosition = SwingConstants.LEFT
      }

      private val iconChooser = JCheckBox("Splash", true).apply { isOpaque = false }
      private val formChooser = JCheckBox("Superellipse", true).apply { isOpaque = false }
      val slider = JSlider(0, 100).apply {
        value = 33
        paintLabels = true
        paintTicks = true
        minorTickSpacing = 1
        majorTickSpacing = 10
        isOpaque = false
      }
      val splashIcon: Icon by lazy {
        findIconUsingNewImplementation(path = ApplicationInfoImpl.getShadowInstanceImpl().splashImageUrl!!,
                                       classLoader = ApplicationInfo::class.java.classLoader)!!
      }
      val generatedIcon: Icon by lazy {
        IconUtil.createImageIcon((createRandomImage(splashIcon.iconWidth, splashIcon.iconHeight) as Image))
      }

      init {
        title = "Rounded Icon Demo"
        isResizable = false
        init()
        slider.addChangeListener(ChangeListener {
          updateIcon()
        })
        updateIcon()
      }

      override fun getStyle(): DialogStyle {
        return DialogStyle.COMPACT
      }

      override fun createCenterPanel(): JComponent {
        val panel = object : BorderLayoutPanel() {
          override fun paintComponent(g: Graphics?) {
            super.paintComponent(g)
            GraphicsUtil.setupAAPainting(g as Graphics2D)
            g.paint = GradientPaint(0f, 0f, UIUtil.getLabelDisabledForeground(), 0f, height.toFloat(), Gray.TRANSPARENT)
            var x = -height
            while (x < width + height) {
              g.drawLine(x, 0, x + height, height)
              g.drawLine(x, 0, x - height, height)
              x += 10
            }
          }
        }.apply { border = createDefaultBorder() }
        val actionListener = ActionListener {
          updateIcon()
        }
        iconChooser.addActionListener(actionListener)
        formChooser.addActionListener(actionListener)

        panel.add(label, CENTER)
        val southPanel = NonOpaquePanel(BorderLayout())
        val chooserPanel = NonOpaquePanel(GridLayout(2, 1))
        chooserPanel.add(iconChooser)
        chooserPanel.add(formChooser)
        southPanel.add(chooserPanel, WEST)
        southPanel.add(slider, CENTER)
        panel.add(southPanel, SOUTH)
        return panel
      }

      fun updateIcon() {
        label.icon = RoundedIcon(/* source = */ if (iconChooser.isSelected) splashIcon else generatedIcon,
                                 /* arcRatio = */ (slider.value * 0.01),
                                 /* superEllipse = */ formChooser.isSelected)
      }

      override fun createActions() = emptyArray<Action>()
    }.show()
  }

  private fun topHalf() = (0.5 + Math.random() / 2).toFloat()

  private fun randomColor() = Color(topHalf(), topHalf(), topHalf(), topHalf())

  private fun createRandomImage(width: Int, height: Int): BufferedImage {
    val image = ImageUtil.createImage(width, height, BufferedImage.TRANSLUCENT)
    val g = image.graphics as Graphics2D
    try {
      g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY)
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g.color = randomColor()
      RectanglePainter.FILL.paint(g, 0, 0, width, height, null)
      for (i in 0 until 100) {
        g.color = randomColor()
        val r = Math.random() * min(width, height) / Math.PI
        g.fill(Ellipse2D.Double(Math.random() * width, Math.random() * height, r, r))
      }
      g.color = Color(0, 0, 0, 85) //mark center of a picture
      g.fill(Ellipse2D.Double((width/2 - 2).toDouble(), (height/2 - 2).toDouble(), 4.toDouble(), 4.toDouble()))
    }
    finally {
      g.dispose()
    }
    return image
  }
}