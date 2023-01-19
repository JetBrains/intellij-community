// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.ui

import com.intellij.ide.customize.transferSettings.TransferSettingsConfiguration
import com.intellij.ide.customize.transferSettings.controllers.TransferSettingsListener
import com.intellij.ide.customize.transferSettings.models.BaseIdeVersion
import com.intellij.ide.customize.transferSettings.models.FailedIdeVersion
import com.intellij.ide.customize.transferSettings.models.TransferSettingsModel
import com.intellij.ide.customize.transferSettings.ui.representation.TransferSettingsRightPanelChooser
import com.intellij.ui.JBColor
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import net.miginfocom.swing.MigLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class TransferSettingsView(private val config: TransferSettingsConfiguration) {
  val panel by lazy { initPanel() }
  val selectedIde: BaseIdeVersion? get() = leftPanel.list.selectedValue

  val model = TransferSettingsModel(config)

  private val leftPanel = TransferSettingsLeftPanel(model.listModel)
  private val contentPanel = JPanel(MigLayout("ins 0, novisualpadding, fill"))

  private val cachedViews = mutableMapOf<BaseIdeVersion, JComponent>()

  init {
    config.controller.addListener(object : TransferSettingsListener {
      override fun reloadPerformed(ideVersion: FailedIdeVersion) {
        cachedViews.remove(ideVersion)
        performRefresh(ideVersion.id)
      }
    })
  }

  private fun initPanel() = JPanel().apply {
    layout = MigLayout("ins 0, novisualpadding, fill")

    add(leftPanel, "west, width 250px, wmax 250px, wmin 250px, growy, pushy, spany")
    add(contentPanel, "east, grow, push, span")
    contentPanel.add(JLabel())

    border = JBUI.Borders.customLineBottom(JBColor.border())

    leftPanel.addListSelectionListener {
      if (selectedValue == null) return@addListSelectionListener
      val view = cachedViews.getOrPut(selectedValue) {
        TransferSettingsRightPanelChooser(selectedValue, config).select().getComponent()
      }
      contentPanel.apply {
        removeAll()
        add(view, "grow, push, span")
        revalidate()
        repaint()
      }
    }

    performRefresh(null)
  }

  private fun performRefresh(selectionTargetId: String?) {
    val newOrdered = model.performRefresh(selectionTargetId)

    leftPanel.list.selectedIndex = if (selectionTargetId != null) newOrdered.indexOfFirst { it.id == selectionTargetId } else 0
  }
}