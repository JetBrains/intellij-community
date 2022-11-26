// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.ui.representation.failed

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.customize.transferSettings.controllers.TransferSettingsController
import com.intellij.ide.customize.transferSettings.models.FailedIdeVersion
import com.intellij.ide.customize.transferSettings.ui.representation.TransferSettingsRepresentationPanel
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.ui.layout.ComponentPredicate
import javax.swing.JComponent

class TransferSettingsFailedIdeRepresentation(private val ide: FailedIdeVersion,
                                              private val controller: TransferSettingsController) : TransferSettingsRepresentationPanel {
  override fun getComponent(): JComponent = panel {
    row {
      icon(AllIcons.General.Warning).align(AlignY.TOP).customize(Gaps(right = 5))
      label(IdeBundle.message("transfersettings.label.failed.to.import.settings.from", ide.name, ide.subName)).bold().customize(Gaps.EMPTY)
    }

    row {
      label(ide.potentialReason ?: IdeBundle.message("transfersettings.label.please.try.again"))
    }

    row {
      button(IdeBundle.message("transfersettings.button.retry.import")) {
        //controller.performReload(ide)
      }.enabledIf(object : ComponentPredicate() {
        override fun addListener(listener: (Boolean) -> Unit) {
          //TODO("Not yet implemented")
        }

        override fun invoke(): Boolean {
          //TODO("Not yet implemented")
          return true
        }
      })
    }
  }
}