// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.importSettings.chooser

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.darcula.ui.OnboardingDialogButtons
import com.intellij.importSettings.data.ImportExternalButtonDataProvider
import com.intellij.importSettings.data.ImportJbButtonDataProvider
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*

class ImportSettingsFromDialog : DialogWrapper(null) {
  private val accountLabel = JLabel("user.name").apply {
    icon = AllIcons.General.User
  }

  private val pane = JPanel(VerticalLayout(JBUI.scale(36), SwingConstants.CENTER)).apply {
    add(JLabel("Import Settings").apply {
      font = Font(font.getFontName(), Font.PLAIN, JBUIScale.scaleFontSize(24f))
    })
  }

  init {
    val importJbButtonDataProvider = ImportJbButtonDataProvider()
    val importExtButtonProvider = ImportExternalButtonDataProvider()

    val jbButtonState = importJbButtonDataProvider.getButtonState()
    val extButtonState = importExtButtonProvider.getButtonState()

    val jbButton = jbButtonState?.let {
      OnboardingDialogButtons.createButton(it.name, it.icon, {})
    }
    val extButton = extButtonState?.let {
      OnboardingDialogButtons.createButton(it.name, it.icon, {})
    }

    val skipImport = OnboardingDialogButtons.createHoveredLinkButton("Skip Import", null, {})

    pane.add(JPanel(VerticalLayout(JBUI.scale(12), SwingConstants.CENTER)).apply {
      jbButton?.let {
        add(it)
      }
      extButton?.let {
       add(it)
      }

      add(skipImport)
    })

    init()
  }

  private fun showListPopup() {

  }

  override fun createCenterPanel(): JComponent {
    return JPanel(GridBagLayout()).apply {
      preferredSize = JBDimension(640, 410)
      val gbc = GridBagConstraints()
      gbc.gridx = 0
      gbc.gridy = 0
      gbc.weightx = 1.0
      gbc.weighty = 1.0
      gbc.fill = GridBagConstraints.NONE
      add(pane, gbc)
    }
  }

  override fun createActions(): Array<Action> {
    return emptyArray()
  }

  override fun createSouthAdditionalPanel(): JPanel? {
    return JPanel().apply {
      add(accountLabel)
    }
  }


  override fun createSouthPanel(leftSideButtons: MutableList<out JButton>,
                                rightSideButtons: MutableList<out JButton>,
                                addHelpToLeftSide: Boolean): JPanel {
    val panel = super.createSouthPanel(leftSideButtons, rightSideButtons, addHelpToLeftSide)

    panel.add(JPanel(GridBagLayout()).apply {
      val c = GridBagConstraints()
      c.fill = GridBagConstraints.NONE
      c.anchor = GridBagConstraints.CENTER
      add(OnboardingDialogButtons.createLinkButton("Other Options", AllIcons.General.ChevronDown, {}), c)

    }, BorderLayout.EAST)

    return panel
  }
}