// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.components

import com.intellij.icons.AllIcons
import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.JBColor
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.util.ui.JBUI
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel

@Suppress("DialogTitleCapitalization")
internal class JBTabsPanel : UISandboxPanel {
  override val title: String = "JBTabsImpl"

  private val place: String = "UI Sandbox"

  override fun createContent(disposable: Disposable): JComponent = panel {
    group("Side actions and components on tabs") {
      row {
        val tabs = JBTabsImpl(project = null, disposable)

        val actions = DefaultActionGroup(MyAction("Action 1", AllIcons.General.GearPlain),
                                         MyAction("Action 2", AllIcons.General.Information))

        tabs.addTab(tab("Nothing"))
        tabs.addTab(tab("Right Actions")
                      .setActions(actions, place))
        tabs.addTab(tab("Right Component")
                      .setSideComponent(createRightComponent()))
        tabs.addTab(tab("Left Component")
                      .setForeSideComponent(createLeftComponent()))
        tabs.addTab(tab("Right Component and Right Actions")
                      .setSideComponent(createRightComponent())
                      .setActions(actions, place))
        tabs.addTab(tab("Left Component and Right Actions")
                      .setForeSideComponent(createLeftComponent())
                      .setActions(actions, place))
        tabs.addTab(tab("Left Component, Right Component and Right Actions")
                      .setSideComponent(createRightComponent())
                      .setForeSideComponent(createLeftComponent())
                      .setActions(actions, place))

        val wrapper = Wrapper(tabs)
        wrapper.border = JBUI.Borders.customLine(JBColor.border())
        cell(wrapper).align(Align.FILL)
      }
    }
  }

  private fun tab(name: String): TabInfo {
    return TabInfo(createContent(name)).setText(name)
  }

  private fun createContent(tabName: String): JComponent {
    return JLabel("'$tabName' tab content")
  }

  private fun createLeftComponent(): JComponent {
    return createSideComponent("Left Component")
  }

  private fun createRightComponent(): JComponent {
    return createSideComponent("Right Component")
  }

  private fun createSideComponent(text: String): JComponent {
    val label = JLabel(text)
    label.border = JBUI.Borders.customLine(JBColor.red)
    return label
  }

  private class MyAction(text: String, icon: Icon? = null) : DumbAwareAction(text, null, icon) {
    override fun actionPerformed(e: AnActionEvent) {
      // do nothing
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  }
}