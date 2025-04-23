// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.jcef.test

import com.intellij.internal.jcef.test.cases.ContextMenu
import com.intellij.internal.jcef.test.cases.KeyboardEvents
import com.intellij.internal.jcef.test.cases.PerformanceTest
import com.intellij.internal.jcef.test.cases.ResourceHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.components.JBList
import java.awt.CardLayout
import java.awt.Component
import java.awt.GridLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.*

internal class JBCefTestApp : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    JBCefTestAppFrame().isVisible = true
  }
}

internal class JBCefTestAppFrame : JFrame() {
  private val cardLayout = CardLayout()
  private val contentPanel: JPanel = JPanel(cardLayout)

  private val testCases: List<TestCase> = listOf(
    KeyboardEvents(), ContextMenu(), ResourceHandler(), PerformanceTest())

  private val tabsList = JBList(testCases.map { it.getDisplayName() })

  init {
    contentPanel.add(JPanel(GridLayout()).apply { add(JLabel("Select the test case")) })
    testCases.forEach { tabName -> contentPanel.add(tabName.getComponent(), tabName.getDisplayName()) }

    tabsList.addListSelectionListener {
      testCases.find { it.getDisplayName() == tabsList.selectedValue }?.initialize()
      cardLayout.show(contentPanel, tabsList.selectedValue)
    }

    val listWithHeader = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      border = BorderFactory.createTitledBorder("Test cases")
      add(JScrollPane(this@JBCefTestAppFrame.tabsList))
    }

    contentPanel.border = BorderFactory.createTitledBorder("Browser Panel")

    val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listWithHeader, contentPanel).apply {
      dividerSize = 1
      isOneTouchExpandable = false
      resizeWeight = 0.0
      setDividerLocation(200)
    }

    contentPane.add(splitPane)

    setSize(800, 600)
    setLocationRelativeTo(null)
    defaultCloseOperation = DISPOSE_ON_CLOSE

    addWindowListener(object : WindowAdapter() {
      override fun windowClosing(e: WindowEvent?) {
        testCases.forEach(Disposable::dispose)
      }
    })
  }

  internal abstract class TestCase : Disposable.Default {
    abstract fun getComponent(): Component
    abstract fun getDisplayName(): String
    fun initialize() {
      if (ready) return
      initializeImpl()
      ready = true
    }

    protected abstract fun initializeImpl()

    private var ready: Boolean = false
  }
}