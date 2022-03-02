package com.intellij.codeInsight.codeVision.ui.model

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionEntryExtraActionModel
import com.intellij.openapi.util.NlsContexts


@NlsContexts.Tooltip
fun CodeVisionEntry.tooltipText(): String = this@tooltipText.tooltip
fun CodeVisionEntry.contextAvailable(): Boolean = this@contextAvailable.extraActions.isNotEmpty()
fun CodeVisionEntryExtraActionModel.isEnabled(): Boolean = this@isEnabled.actionId != null
