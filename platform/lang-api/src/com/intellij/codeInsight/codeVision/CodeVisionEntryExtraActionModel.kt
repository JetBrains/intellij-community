package com.intellij.codeInsight.codeVision

/**
 * @property displayText Text that will be displayed in the UI
 * @property actionId Action ID passed to provider when this action is invoked. null for non-clickable line
 */
data class CodeVisionEntryExtraActionModel(val displayText: String,
                                           val actionId: String?)