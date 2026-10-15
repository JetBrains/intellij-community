package com.intellij.codeInsight.codeVision

import org.jetbrains.annotations.Nls

/**
 * @property displayText Text that will be displayed in the UI
 * @property actionId Action ID passed to provider when this action is invoked. null for non-clickable line
 */
data class CodeVisionEntryExtraActionModel(@Nls val displayText: String,
                                           val actionId: String?) {
  var children: List<CodeVisionEntryExtraActionModel> = emptyList()
    private set

  constructor(@Nls displayText: String,
              actionId: String?,
              children: List<CodeVisionEntryExtraActionModel>) : this(displayText, actionId) {
    this.children = children
  }
}