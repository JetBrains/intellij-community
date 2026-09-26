// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename

import com.intellij.codeInsight.codeVision.settings.CodeVisionGroupSettingProvider
import com.intellij.codeInsight.codeVision.settings.PlatformCodeVisionIds
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class RenameCodeVisionSettingProvider : CodeVisionGroupSettingProvider {
  override val groupId: String
    get() = PlatformCodeVisionIds.RENAME.key
}