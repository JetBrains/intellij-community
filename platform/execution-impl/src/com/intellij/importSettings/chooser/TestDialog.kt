// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.importSettings.chooser

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class TestDialog(val group: ActionGroup, val act: AnAction) : DialogWrapper(null) {
  private val pane = ImportSettingsFromPane(group)

  init {
    init()
  }

  override fun createCenterPanel(): JComponent {
    return pane
  }

  override fun createSouthPanel(leftSideButtons: MutableList<out JButton>,
                                rightSideButtons: MutableList<out JButton>,
                                addHelpToLeftSide: Boolean): JPanel {
    val act = ActionManager.getInstance().createActionToolbar("BlaBlaBLa", DefaultActionGroup().apply { add(act) }, false).apply {
      if (this is ActionToolbarImpl) {
        isOpaque = false
        setActionButtonBorder(2, JBUI.CurrentTheme.RunWidget.toolbarBorderHeight())
      }

    }

    val panel = super.createSouthPanel(leftSideButtons, rightSideButtons, addHelpToLeftSide)

    /*panel.add(JPanel(VerticalLayout(0, SwingConstants.RIGHT)).apply {*/
    panel.add(JPanel(GridBagLayout()).apply {
      val c = GridBagConstraints()
      c.fill = GridBagConstraints.NONE
      c.anchor = GridBagConstraints.CENTER

      add(act.component, c)
    }, BorderLayout.EAST)



    return panel
  }

}