// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui.util

import com.intellij.execution.ui.SettingsEditorFragmentType
import com.intellij.openapi.util.NlsContexts

interface LabeledSettingsFragmentInfo : SettingsFragmentInfo {
  val editorLabel: @NlsContexts.Label String

  override val settingsPriority: Int get() = 0
  override val settingsType get() = SettingsEditorFragmentType.EDITOR
}