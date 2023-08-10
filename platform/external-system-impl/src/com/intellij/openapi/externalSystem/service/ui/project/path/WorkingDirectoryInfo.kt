// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui.project.path

import com.intellij.openapi.externalSystem.service.ui.util.LabeledSettingsFragmentInfo
import com.intellij.openapi.roots.ui.distribution.FileChooserInfo
import com.intellij.openapi.util.ModificationTracker
import org.jetbrains.annotations.Nls

interface WorkingDirectoryInfo : FileChooserInfo, LabeledSettingsFragmentInfo {
  override val settingsId: String get() = "external.system.working.directory.fragment"
  override val settingsGroup: String? get() = null
  override val settingsPriority: Int get() = -10
  override val settingsHint: String? get() = null
  override val settingsActionHint: String? get() = null

  override val fileChooserDescription: String? get() = null
  override val fileChooserMacroFilter get() = FileChooserInfo.DIRECTORY_PATH

  val emptyFieldError: @Nls(capitalization = Nls.Capitalization.Sentence) String

  val externalProjectModificationTracker: ModificationTracker
    get() = ModificationTracker.NEVER_CHANGED

  suspend fun collectExternalProjects(): List<ExternalProject>
}