// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl.ui

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.util.containers.ContainerUtil
import java.util.*
import javax.swing.JList

class WslDistributionComboBox(initial: WSLDistribution?,
                              setPreferredSizeToShowLongMessagesFully: Boolean) : ComboBox<WSLDistribution?>() {
  var selected: WSLDistribution?
    get() = model.selected
    set(value) {
      if (model.getElementIndex(value) < 0) {
        model.add(value)
      }
      selectedItem  = value
    }
  val isSelectedValid: Boolean
    get() {
      val selectedDistribution = selected
      val installedDistributions = allInstalledDistributions
      return selectedDistribution != null && (installedDistributions == null || installedDistributions.contains(selectedDistribution))
    }
  private val model: CollectionComboBoxModel<WSLDistribution?> = CollectionComboBoxModel<WSLDistribution?>()
  private var allInstalledDistributions: List<WSLDistribution>? = null

  init {
    setModel(model)
    renderer = WslDistributionRenderer()

    if (initial != null) {
      model.add(initial)
      model.selectedItem = initial
    }
    else if (setPreferredSizeToShowLongMessagesFully) {
      // show "No installed distributions" message fully
      allInstalledDistributions = emptyList()
      model.add(null)
      preferredSize = preferredSize
      model.removeAll()
      allInstalledDistributions = null
    }

    WslDistributionManager.getInstance().installedDistributionsFuture.thenAccept { installedDistributions: List<WSLDistribution> ->
      ApplicationManager.getApplication().invokeLater(
        {
          allInstalledDistributions = installedDistributions
          val newDistributions: MutableList<WSLDistribution> = ArrayList(installedDistributions)
          var selected: WSLDistribution? = model.selected
          tryAdd(selected, newDistributions)
          model.removeAll()
          model.addAll(0, newDistributions)
          if (selected == null) {
            selected = ContainerUtil.getFirstItem(model.items)
          }
          model.selectedItem = selected
        }, ModalityState.any())
    }
  }

  private fun tryAdd(distribution: WSLDistribution?, distributions: MutableList<WSLDistribution>) {
    if (distribution != null && !distributions.contains(distribution)) {
      distributions.add(distribution)
    }
  }

  private inner class WslDistributionRenderer : ColoredListCellRenderer<WSLDistribution?>() {

    override fun customizeCellRenderer(list: JList<out WSLDistribution?>,
                                       value: WSLDistribution?,
                                       index: Int,
                                       selected: Boolean,
                                       hasFocus: Boolean) {
      val installedList = allInstalledDistributions
      if (value == null) {
        if (installedList != null) {
          append(IdeBundle.message("wsl.no.installed.distributions"))
        }
        else {
          append(IdeBundle.message("progress.text.loading"))
        }
      }
      else {
        if (installedList != null && !installedList.contains(value)) {
          append(IdeBundle.message("wsl.not.installed.distribution", value.msId))
        }
        else {
          append(value.msId)
        }
      }
    }
  }
}
