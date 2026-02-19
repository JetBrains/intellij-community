// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision.ui.model

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionEntryExtraActionModel
import com.intellij.openapi.util.NlsContexts

@NlsContexts.Tooltip
internal fun CodeVisionEntry.tooltipText(): String = this@tooltipText.tooltip

internal fun CodeVisionEntry.contextAvailable(): Boolean = this@contextAvailable.extraActions.isNotEmpty()

internal fun CodeVisionEntryExtraActionModel.isEnabled(): Boolean = this@isEnabled.actionId != null
