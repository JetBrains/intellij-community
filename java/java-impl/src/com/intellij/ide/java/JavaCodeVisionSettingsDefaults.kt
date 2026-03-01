// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.java

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.settings.CodeVisionSettingsDefaults

internal class JavaCodeVisionSettingsDefaults : CodeVisionSettingsDefaults {
  override val defaultPosition: CodeVisionAnchorKind
    get() = CodeVisionAnchorKind.Right
}