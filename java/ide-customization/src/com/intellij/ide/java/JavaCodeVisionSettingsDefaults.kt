package com.intellij.ide.java

import com.intellij.codeInsight.codeVision.CodeVisionAnchorKind
import com.intellij.codeInsight.codeVision.settings.CodeVisionSettingsDefaults

internal class JavaCodeVisionSettingsDefaults : CodeVisionSettingsDefaults {
  override val defaultPosition: CodeVisionAnchorKind = CodeVisionAnchorKind.Right
}