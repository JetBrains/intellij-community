// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.ui.representation

import com.intellij.ide.customize.transferSettings.TransferSettingsConfiguration
import com.intellij.ide.customize.transferSettings.models.BaseIdeVersion
import com.intellij.ide.customize.transferSettings.models.FailedIdeVersion
import com.intellij.ide.customize.transferSettings.models.IdeVersion
import com.intellij.ide.customize.transferSettings.ui.representation.failed.TransferSettingsFailedIdeRepresentation
import com.intellij.ide.customize.transferSettings.ui.representation.ideVersion.TransferSettingsIdeRepresentation

class TransferSettingsRightPanelChooser(private val ideV: BaseIdeVersion, private val config: TransferSettingsConfiguration) {
  fun select(): TransferSettingsRepresentationPanel = when (ideV) {
    is IdeVersion -> TransferSettingsIdeRepresentation(ideV as IdeVersion, config.getSectionsFactory())
    is FailedIdeVersion -> TransferSettingsFailedIdeRepresentation(ideV, config.controller)
    else -> error("Unknown type of BaseIdeVersion")
  }
}