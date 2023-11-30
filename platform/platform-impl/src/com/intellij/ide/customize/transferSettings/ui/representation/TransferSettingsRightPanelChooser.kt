// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.ui.representation

import com.intellij.ide.customize.transferSettings.TransferSettingsConfiguration
import com.intellij.ide.customize.transferSettings.models.BaseIdeVersion
import com.intellij.ide.customize.transferSettings.models.FailedIdeVersion
import com.intellij.ide.customize.transferSettings.models.IdeVersion
import com.intellij.ide.customize.transferSettings.ui.representation.failed.TransferSettingsFailedIdeRepresentation
import com.intellij.ide.customize.transferSettings.ui.representation.ideVersion.TransferSettingsIdeRepresentation
import javax.swing.JComponent
import javax.swing.JLabel

open class TransferSettingsRightPanelChooser(private val ideV: BaseIdeVersion, protected val config: TransferSettingsConfiguration) {
  open fun select(): TransferSettingsRepresentationPanel = when (ideV) {
    is IdeVersion -> TransferSettingsIdeRepresentation(ideV, config.getSectionsFactory(), getBottomComponentFactory())
    is FailedIdeVersion -> TransferSettingsFailedIdeRepresentation(ideV, config.controller)
    else -> error("Unknown type of BaseIdeVersion")
  }

  open fun getBottomComponentFactory(): (() -> JComponent?)? = null
}