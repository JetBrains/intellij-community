package com.intellij.codeInsight.codeVision.ui.popup

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionEntryExtraActionModel
import com.intellij.codeInsight.codeVision.ui.model.CodeVisionListData
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.ui.popup.list.ListPopupImpl


class CodeVisionContextPopup private constructor(project: Project, aStep: ListPopupStep<CodeVisionEntryExtraActionModel>) : ListPopupImpl(
  project,
  aStep) {
  companion object {

    fun createLensList(entry: CodeVisionEntry, model: CodeVisionListData, project: Project): CodeVisionContextPopup {
      val lst = entry.extraActions + model.projectModel.hideLens

      val aStep = object : SubCodeVisionMenu(lst,
                                             {
                                               model.rangeCodeVisionModel.handleLensExtraAction(entry, it)
                                             }) {


        override fun getSeparatorAbove(value: CodeVisionEntryExtraActionModel): ListSeparator? {
          return super.getSeparatorAbove(value) ?: if (value == model.projectModel.hideLens) ListSeparator() else null
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