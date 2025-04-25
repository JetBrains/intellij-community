// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.jcef.test.cases

import com.intellij.internal.jcef.test.JBCefTestAppFrame
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.components.JBScrollPane
import java.awt.*
import javax.swing.*


internal class PerformanceTest : JBCefTestAppFrame.TestCase() {
  override fun getDisplayName() = "Performance Tests"

  override fun getComponent(): Component {
    val panel = JPanel(VerticalFlowLayout(FlowLayout.LEFT))
    panel.add(createTestCaseItem("Resize test", "Measures the time to redraw the component after resize", "Start Test", ::runSimpleResizeTest))
    panel.add(createTestCaseItem("Manual scrolling test", "Manual scrolling with drawing diagram", "Start Test", ::runScrollingTest))

    val scrollPane = JBScrollPane(panel)
    scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED

    return scrollPane
  }

  override fun initializeImpl() {
  }

  private fun createTestCaseItem(title: String, description: String, buttonText: String, runTest: () -> Unit): JPanel {
    val section = JPanel(BorderLayout(10, 5))
    section.border = BorderFactory.createCompoundBorder(
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

    val startButton = JButton(buttonText)
    startButton.addActionListener { runTest() }

    section.add(textPanel, BorderLayout.CENTER)
    section.add(startButton, BorderLayout.EAST)

    return section
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