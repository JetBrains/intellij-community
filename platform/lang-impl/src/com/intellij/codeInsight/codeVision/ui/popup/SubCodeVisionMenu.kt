package com.intellij.codeInsight.codeVision.ui.popup

import com.intellij.codeInsight.codeVision.CodeVisionEntryExtraActionModel
import com.intellij.codeInsight.codeVision.ui.model.isEnabled
import com.intellij.openapi.ui.popup.ListPopupStepEx
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.ui.StatusText

open class SubCodeVisionMenu(val list: List<CodeVisionEntryExtraActionModel>,
                             val onClick: (String) -> Unit,
                             @NlsContexts.PopupTitle title: String? = null) :
  BaseListPopupStep<CodeVisionEntryExtraActionModel>(title, list.filter { it.isEnabled() }),
  ListPopupStepEx<CodeVisionEntryExtraActionModel> {

  override fun hasSubstep(selectedValue: CodeVisionEntryExtraActionModel?): Boolean {
    return selectedValue?.children?.isNotEmpty() ?: false
  }

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
    if (value.children.isNotEmpty()) {
      return SubCodeVisionMenu(value.children, onClick)
    }

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

  override fun getTooltipTextFor(value: CodeVisionEntryExtraActionModel?): @NlsContexts.Tooltip String? = null

  override fun setEmptyText(emptyText: StatusText) { }
}