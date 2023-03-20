// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings

import com.intellij.ide.customize.transferSettings.controllers.TransferSettingsController
import com.intellij.ide.customize.transferSettings.controllers.TransferSettingsControllerImpl
import com.intellij.ide.customize.transferSettings.models.IdeVersion
import com.intellij.ide.customize.transferSettings.ui.representation.ideVersion.sections.TransferSettingsSection
import com.intellij.ide.customize.transferSettings.ui.representation.ideVersion.sections.KeymapSection
import com.intellij.ide.customize.transferSettings.ui.representation.ideVersion.sections.PluginsSection
import com.intellij.ide.customize.transferSettings.ui.representation.ideVersion.sections.RecentProjectsSection

interface TransferSettingsConfiguration {
  val dataProvider: TransferSettingsDataProvider
  val shouldDisplayFailedVersions: Boolean

  val controller: TransferSettingsController

  fun getSectionsFactory(): (IdeVersion) -> List<TransferSettingsSection>
}

open class DefaultTransferSettingsConfiguration(
  override val dataProvider: TransferSettingsDataProvider,
  override val shouldDisplayFailedVersions: Boolean
) : TransferSettingsConfiguration {
  override val controller = TransferSettingsControllerImpl()
  override fun getSectionsFactory(): (IdeVersion) -> List<TransferSettingsSection> {
    return {
      listOf(
        KeymapSection(it),
        PluginsSection(it),
        RecentProjectsSection(it)
      )
    }
  }
}