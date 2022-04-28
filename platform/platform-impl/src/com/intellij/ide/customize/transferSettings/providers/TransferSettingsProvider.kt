// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.providers

import com.intellij.ide.customize.transferSettings.models.BaseIdeVersion
import com.intellij.ide.customize.transferSettings.models.IdeVersion
import com.intellij.ide.customize.transferSettings.models.SettingsPreferencesKind

interface TransferSettingsProvider { // ex. AbstractTransferSettingsProvider
  val name: String
  fun isAvailable(): Boolean
  fun getIdeVersions(skipIds: List<String>): List<BaseIdeVersion>
  fun getImportPerformer(ideVersion: IdeVersion): ImportPerformer
  fun getSupportedFeatures(): List<SettingsPreferencesKind> = SettingsPreferencesKind.keysWithoutNone
}