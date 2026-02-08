// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.jcef.test.cases

import com.intellij.internal.jcef.test.JBCefTestAppFrame
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.UIManager


internal class PerformanceTest : JBCefTestAppFrame.TestCase() {
  override fun getDisplayName() = "Performance Tests"

  override fun getComponent(): Component {
    val panel = JPanel(VerticalFlowLayout(FlowLayout.LEFT))
    panel.add(TestCasePanel("Resize test", "Measures the time to redraw the component after resize", "Start Test", ::runSimpleResizeTest))
    panel.add(TestCasePanel("Manual scrolling test", "Manual scrolling with the scrolling requested/performed diagram", "Start Test", ::runScrollingTest))
    panel.add(TestCasePanel("FPS test", "A simple FPS test", "Start Test", ::runFpsTest))
    panel.add(TestCasePanel("CPU usage", "JCEF CPU usage statistic", "Show", ::showCpuUsage))

    val scrollPane = JBScrollPane(panel)
    scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED

    return scrollPane
  }

  override fun initializeImpl() {
  }

class RepaintListener(component: Component, var onRepaint: () -> Unit) : JPanel() {
  constructor(component: Component) : this(component, {})
    init {
      layout = BorderLayout()
      add(component)
    }

    override fun paint(g: Graphics?) {
      super.paint(g)
      onRepaint()
    }
  }
}

internal class TestCasePanel : JPanel {
  constructor() : super(BorderLayout(10, 5))
  constructor(title: String, description: String, runComponent: JComponent) : this() {
    initializeImpl(title, description, runComponent)
  }
  private fun initializeImpl(title: String, description: String, runComponent: JComponent) {
    this.border = BorderFactory.createCompoundBorder(
      BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")),
      BorderFactory.createEmptyBorder(10, 10, 10, 10)
    )

    val titleLabel = JLabel(title)
    titleLabel.font = titleLabel.font.deriveFont(Font.BOLD)

    val descLabel = JLabel(description)

    val textPanel = JPanel(BorderLayout(0, 5))
    textPanel.isOpaque = false
    textPanel.add(titleLabel, BorderLayout.NORTH)
    textPanel.add(descLabel, BorderLayout.CENTER)

    this.add(textPanel, BorderLayout.CENTER)
    this.add(runComponent, BorderLayout.EAST)
  }

  constructor(title: String, description: String, buttonText: String, runTest: () -> Unit) : this() {
    val startButton = JButton(buttonText)
    startButton.addActionListener { runTest() }
    initializeImpl(title, description, startButton)
  }
}
