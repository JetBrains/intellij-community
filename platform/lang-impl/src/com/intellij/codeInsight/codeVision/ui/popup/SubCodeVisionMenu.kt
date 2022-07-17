package com.intellij.codeInsight.codeVision.ui.popup

import com.intellij.codeInsight.codeVision.CodeVisionEntryExtraActionModel
import com.intellij.codeInsight.codeVision.ui.model.isEnabled
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.NlsContexts

open class SubCodeVisionMenu(val list: List<CodeVisionEntryExtraActionModel>,
                             val onClick: (String) -> Unit,
                             @NlsContexts.PopupTitle title: String? = null) :
  BaseListPopupStep<CodeVisionEntryExtraActionModel>(title, list.filter { it.isEnabled() }) {
  override fun isSelectable(value: CodeVisionEntryExtraActionModel): Boolean {
    return value.isEnabled()
  }

  override fun isAutoSelectionEnabled(): Boolean {
    return false
  }

  override fun isSpeedSearchEnabled(): Boolean {
    return true
  }

  override fun isMnemonicsNavigationEnabled(): Boolean {
    return true
  }

  override fun getTextFor(value: CodeVisionEntryExtraActionModel): String {
    return value.displayText
  }

  override fun onChosen(value: CodeVisionEntryExtraActionModel, finalChoice: Boolean): PopupStep<*>? {
    value.actionId?.let {
      doFinalStep {
        onClick.invoke(value.actionId!!)
      }
    }
    return PopupStep.FINAL_CHOICE
  }

  override fun getSeparatorAbove(value: CodeVisionEntryExtraActionModel): ListSeparator? {
    val index = list.indexOf(value)
    val prevIndex = index - 1
    if (prevIndex >= 0) {
      val prevValue = list[prevIndex]

      if (!prevValue.isEnabled()) {
        return ListSeparator(prevValue.displayText)
      }
    }

    return null
  }
}