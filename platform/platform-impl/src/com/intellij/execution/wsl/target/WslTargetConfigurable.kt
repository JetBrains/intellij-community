// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl.target

import com.intellij.execution.target.getTargetType
import com.intellij.execution.wsl.ui.WslDistributionComboBox
import com.intellij.ide.IdeBundle
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.*

class WslTargetConfigurable(val config: WslTargetEnvironmentConfiguration) :
  BoundConfigurable(config.displayName, config.getTargetType().helpTopic) {

  override fun createPanel(): DialogPanel = panel {
    row(label = IdeBundle.message("wsl.linux.distribution.label")) {
      val comboBox = WslDistributionComboBox(null, true)
      comboBox().withBinding(
        { c -> c.selected },
        { c, v -> c.selected = v },
        PropertyBinding(
          { config.distribution },
          { config.distribution = it }
        )
      )
    }
  }
}
