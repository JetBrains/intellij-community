// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl.target

import com.intellij.execution.target.getTargetType
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WSLUtil
import com.intellij.ide.IdeBundle
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Ref
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.layout.*
import java.util.ArrayList
import javax.swing.JList

class WslTargetConfigurable(val config: WslTargetEnvironmentConfiguration) :
  BoundConfigurable(config.displayName, config.getTargetType().helpTopic) {

  override fun createPanel(): DialogPanel = panel {
    row(label = IdeBundle.message("wsl.linux.distribution.label")) {
      val comboBox: ComboBox<Ref<WSLDistribution?>> = createComboBox()
      comboBox().withBinding(
        { c ->
          (c.selectedItem as? Ref<*>)?.get() as? WSLDistribution
        },
        { c, v -> c.selectedItem = Ref.create(v) },
        PropertyBinding(
          { config.distribution },
          { config.distribution = it }
        )
      )
    }
  }

  private fun createComboBox(): ComboBox<Ref<WSLDistribution?>> {
    val model = CollectionComboBoxModel(ArrayList(WSLUtil.getAvailableDistributions().map { Ref.create(it) }))
    val comboBox: ComboBox<Ref<WSLDistribution?>> = ComboBox()
    comboBox.model = model
    comboBox.renderer = WslDistributionRenderer()
    comboBox.selectedItem = Ref.create(config.distribution)
    comboBox.prototypeDisplayValue = Ref.create()
    return comboBox
  }

  private class WslDistributionRenderer : ColoredListCellRenderer<Ref<WSLDistribution?>>() {
    override fun customizeCellRenderer(list: JList<out Ref<WSLDistribution?>>,
                                       value: Ref<WSLDistribution?>?,
                                       index: Int,
                                       selected: Boolean,
                                       hasFocus: Boolean) {
      append(value?.get()?.presentableName ?: IdeBundle.message("wsl.no.available.distributions"))
    }
  }
}
