// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision.ui.model

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.Nls
import javax.swing.Icon

internal class ZombieCodeVisionEntry(
  providerId: String,
  @Nls longPresentation: String,
  @NlsContexts.Tooltip tooltip: String,
  icon: Icon?,
  val count: Int?,
  val text: String = longPresentation,
) : CodeVisionEntry(providerId, icon, longPresentation, tooltip, emptyList()) {

  override fun toString() = "ZombieCodeVisionEntry('$text')"
}
