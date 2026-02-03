package com.intellij.codeInsight.codeVision.ui.popup

import com.intellij.codeInsight.codeVision.*
import com.intellij.codeInsight.codeVision.ui.model.CodeVisionListData
import com.intellij.codeInsight.codeVision.ui.model.ProjectCodeVisionModel
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.ui.popup.list.ListPopupImpl

class CodeVisionContextPopup private constructor(project: Project, aStep: ListPopupStep<CodeVisionEntryExtraActionModel>)
  : ListPopupImpl(project, aStep) {

  companion object {
    fun createLensList(entry: CodeVisionEntry, model: CodeVisionListData, project: Project): CodeVisionContextPopup {
      val activeProvider = CodeVisionInitializer.getInstance(project).getCodeVisionHost().getProviderById(entry.providerId) ?: error("Can't find provider with id: ${entry.providerId}")
      val lst = entry.extraActions +
                CodeVisionEntryExtraActionModel(CodeVisionMessageBundle.message("action.hide.this.metric.text", activeProvider.name), ProjectCodeVisionModel.HIDE_PROVIDER_ID) +
                CodeVisionEntryExtraActionModel(CodeVisionMessageBundle.message("action.hide.all.text"), ProjectCodeVisionModel.HIDE_ALL) +
                CodeVisionEntryExtraActionModel(CodeVisionMessageBundle.message("LensListPopup.tooltip.settings"), CodeVisionHost.settingsLensProviderId)

      val aStep = object : SubCodeVisionMenu(lst,
                                             {
                                               model.rangeCodeVisionModel.handleLensExtraAction(entry, it)
                                             }) {


        override fun getSeparatorAbove(value: CodeVisionEntryExtraActionModel): ListSeparator? {
          return null
        }
      }

      return CodeVisionContextPopup(project, aStep)
    }
  }

  init {
    setMaxRowCount(15)
  }

  override fun beforeShow(): Boolean {
    setHandleAutoSelectionBeforeShow(false)
    list.clearSelection()
    return super.beforeShow()
  }

  override fun afterShow() {
  }
}