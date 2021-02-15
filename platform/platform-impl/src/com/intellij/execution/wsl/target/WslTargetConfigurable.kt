// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl.target

import com.intellij.execution.target.getTargetType
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.layout.*
import javax.swing.JList

class WslTargetConfigurable(val config: WslTargetEnvironmentConfiguration) :
  BoundConfigurable(config.displayName, config.getTargetType().helpTopic) {

  override fun createPanel(): DialogPanel = panel {
    row(label = IdeBundle.message("wsl.linux.distribution.label")) {
      val comboBox: ComboBox<WSLDistribution?> = createComboBox()
      comboBox().withBinding(
        { c -> c.selectedItem as? WSLDistribution },
        { c, v -> c.selectedItem = v },
        PropertyBinding(
          { config.distribution },
          { config.distribution = it }
        )
      )
    }
  }

  private fun createComboBox(): ComboBox<WSLDistribution?> {
    val model = CollectionComboBoxModel<WSLDistribution?>()
    val comboBox: ComboBox<WSLDistribution?> = ComboBox()
    comboBox.model = model
    comboBox.renderer = WslDistributionRenderer()

    val selectedDistribution: WSLDistribution? = config.distribution
    if (selectedDistribution != null) {
      model.add(selectedDistribution)
    }
    else {
      // show "No available distributions" message fully
      model.add(null)
      comboBox.preferredSize = comboBox.preferredSize
      model.removeAll()
    }

    WslDistributionManager.getInstance().installedDistributionsFuture.thenAccept { distributions ->
      var result = distributions
      if (selectedDistribution != null && distributions.find { it.msId.equals(selectedDistribution.msId, ignoreCase = true) } == null) {
        result = distributions + selectedDistribution
      }
      ApplicationManager.getApplication().invokeLater(Runnable {
        model.removeAll()
        model.addAll(0, result)
      }, ModalityState.any())
    }
    return comboBox
  }

  private class WslDistributionRenderer : ColoredListCellRenderer<WSLDistribution?>() {
    override fun customizeCellRenderer(list: JList<out WSLDistribution?>,
                                       value: WSLDistribution?,
                                       index: Int,
                                       selected: Boolean,
                                       hasFocus: Boolean) {
      append(value?.presentableName ?: IdeBundle.message("wsl.no.available.distributions"))
    }
  }
}
