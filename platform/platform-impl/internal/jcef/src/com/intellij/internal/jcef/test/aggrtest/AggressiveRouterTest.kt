// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.jcef.test.aggrtest;


import com.intellij.internal.jcef.test.JBCefTestAppFrame
import com.intellij.internal.jcef.test.JCEFNonModalDialog
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

internal class AggressiveRouterTest : JBCefTestAppFrame.TestCase() {
  override fun getComponent(): Component = myComponent

  override fun getDisplayName(): String = "Aggressive message router test"

  override fun initializeImpl() {
    myComponent.removeAll()
    myComponent.add(createContent(ProjectManager.getInstance().getDefaultProject()), BorderLayout.SOUTH)
  }

  fun createContent(project: Project) = JBPanel<JBPanel<*>>(BorderLayout()).apply {
    val mainPanel = JBPanel<JBPanel<*>>().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      border = EmptyBorder(10, 10, 10, 10)
    }

    val titleLabel = JBLabel("JCEF JBR-9559 Reproduction Tool").apply {
      font = font.deriveFont(16f).deriveFont(java.awt.Font.BOLD)
    }

    val jcefTestButtonNullRef = JButton("Open JCEF aggressive router test").apply {
      addActionListener {
        val dialog = JCEFNonModalDialog(JCEFAggressiveRouterTest(), "JBR-9559 reproducer test")
        dialog.show()
      }
    }

    // Add components with spacing
    mainPanel.add(titleLabel)
    mainPanel.add(javax.swing.Box.createVerticalStrut(10))
    mainPanel.add(javax.swing.Box.createVerticalStrut(15))
    mainPanel.add(javax.swing.Box.createVerticalStrut(15))
    mainPanel.add(jcefTestButtonNullRef)
    mainPanel.add(javax.swing.Box.createVerticalStrut(10))
    mainPanel.add(javax.swing.Box.createVerticalStrut(15))
    mainPanel.add(javax.swing.Box.createVerticalStrut(10))

    add(mainPanel, BorderLayout.CENTER)
  }

  private val myComponent = JPanel(BorderLayout())
}

