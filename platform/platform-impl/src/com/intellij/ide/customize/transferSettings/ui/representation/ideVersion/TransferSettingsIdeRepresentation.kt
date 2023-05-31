// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.ui.representation.ideVersion

import com.intellij.ide.customize.transferSettings.models.IdeVersion
import com.intellij.ide.customize.transferSettings.ui.representation.TransferSettingsRepresentationPanel
import com.intellij.ide.customize.transferSettings.ui.representation.ideVersion.sections.TransferSettingsSection
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps

class TransferSettingsIdeRepresentation(ideVersion: IdeVersion, sectionFactory: (IdeVersion) -> List<TransferSettingsSection>)
  : TransferSettingsRepresentationPanel {
  private val sections = sectionFactory(ideVersion)

  override fun getComponent(): DialogPanel = panel {
    for (section in sections) {
      if (!section.worthShowing()) continue

      row {
        cell(section.getUI()).customize(UnscaledGaps(bottom = 20))
      }
    }
  }
}