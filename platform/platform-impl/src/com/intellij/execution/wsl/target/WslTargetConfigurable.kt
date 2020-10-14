// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl.target

import com.intellij.execution.target.getTargetType
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WSLUtil
import com.intellij.ide.IdeBundle
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.layout.*
import java.util.ArrayList
import javax.swing.JList

class WslTargetConfigurable(val config: WslTargetEnvironmentConfiguration) :
  BoundConfigurable(config.displayName, config.getTargetType().helpTopic) {

  override fun createPanel(): DialogPanel = panel {
    row(label = IdeBundle.message("wsl.linux.distribution.label")) {
      val model = CollectionComboBoxModel(ArrayList(WSLUtil.getAvailableDistributions()))
      val comboBox: ComboBox<WSLDistribution?> = ComboBox<WSLDistribution?>()
      comboBox.model = model
      comboBox.selectedItem
      comboBox.renderer = WslDistributionRenderer()
      comboBox.selectedItem = config.distribution
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

  private class WslDistributionRenderer : ColoredListCellRenderer<WSLDistribution>() {
    override fun customizeCellRenderer(list: JList<out WSLDistribution?>,
                                       value: WSLDistribution?,
                                       index: Int,
                                       selected: Boolean,
                                       hasFocus: Boolean) {
      append(value?.presentableName ?: IdeBundle.message("wsl.no.available.distributions"))
    }
  }
}
