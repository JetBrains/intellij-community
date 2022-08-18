// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.codeVision

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.codeVision.settings.PlatformCodeVisionIds

abstract class InheritorsCodeVisionProvider : CodeVisionProviderBase() {

  override val name: String
    get() = CodeInsightBundle.message("settings.inlay.hints.inheritors")
  override val groupId: String
    get() = PlatformCodeVisionIds.INHERITORS.key
}