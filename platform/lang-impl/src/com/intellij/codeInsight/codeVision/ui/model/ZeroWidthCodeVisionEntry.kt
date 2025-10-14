// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision.ui.model

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ZeroWidthCodeVisionEntry(
  providerId: String
) : CodeVisionEntry(
  providerId,
  icon = null,
  longPresentation = "",
  tooltip = "",
  extraActions = emptyList()
)
