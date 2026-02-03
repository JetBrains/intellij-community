// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.ui.util

import com.intellij.openapi.roots.ui.distribution.FileChooserInfo

interface PathFragmentInfo : FileChooserInfo, LabeledSettingsFragmentInfo {
  override val settingsHint: String? get() = null
  override val settingsActionHint: String? get() = null
}
