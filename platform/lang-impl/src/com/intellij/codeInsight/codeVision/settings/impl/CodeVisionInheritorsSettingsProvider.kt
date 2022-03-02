// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.codeVision.settings.impl

import com.intellij.codeInsight.codeVision.settings.CodeVisionGroupSettingProvider
import com.intellij.codeInsight.codeVision.settings.PlatformCodeVisionIds

class CodeVisionInheritorsSettingsProvider : CodeVisionGroupSettingProvider {
  override val groupId: String
    get() = PlatformCodeVisionIds.INHERITORS.key
}