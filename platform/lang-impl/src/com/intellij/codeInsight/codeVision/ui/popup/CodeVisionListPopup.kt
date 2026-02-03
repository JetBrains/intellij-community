package com.intellij.codeInsight.codeVision.ui.popup

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.codeInsight.codeVision.CodeVisionMessageBundle
import com.intellij.codeInsight.codeVision.ui.model.AdditionalCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.RangeCodeVisionModel
import com.intellij.codeInsight.codeVision.ui.model.contextAvailable
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.ui.popup.list.ListPopupImpl
import javax.swing.Icon

class CodeVisionListPopup private constructor(project: Project, aStep: ListPopupStep<CodeVisionEntry>) : ListPopupImpl(project, aStep) {
  companion object {

    private val settingButton = AdditionalCodeVisionEntry(CodeVisionHost.settingsLensProviderId, CodeVisionMessageBundle.message("LensListPopup.tooltip.settings.settings"),
                                                          CodeVisionMessageBundle.message("LensListPopup.tooltip.settings"))

    fun createLensList(model: RangeCodeVisionModel, project: Project, inlay: Inlay<*>): CodeVisionListPopup {
      val lst: ArrayList<CodeVisionEntry> = ArrayList(model.sortedLensesMorePopup())
      lst.add(settingButton)

      val hotkey = KeymapUtil.getShortcutsText(KeymapManager.getInstance()!!.activeKeymap.getShortcuts("CodeLens.ShowMore"))

      val aStep = object : BaseListPopupStep<CodeVisionEntry>("${model.name}${if (hotkey.isEmpty()) "" else "($hotkey)"}", lst) {
        override fun isAutoSelectionEnabled(): Boolean {
          return true
        }

        override fun isSpeedSearchEnabled(): Boolean {
          return true
        }

        override fun isMnemonicsNavigationEnabled(): Boolean {
          return true
        }

        override fun getTextFor(value: CodeVisionEntry): String {
          return value.longPresentation
        }

        override fun getIconFor(value: CodeVisionEntry): Icon? {
          return value.icon
        }

        override fun onChosen(selectedValue: CodeVisionEntry, finalChoice: Boolean): PopupStep<*>? {
          if (hasSubstep(selectedValue)) {
            return SubCodeVisionMenu(selectedValue.extraActions,
                                     {
                                       model.handleLensExtraAction(selectedValue, it)
                                     }
            )
          }

          doFinalStep {
            model.handleLensClick(selectedValue, inlay)
          }
          return PopupStep.FINAL_CHOICE
        }

        override fun hasSubstep(selectedValue: CodeVisionEntry): Boolean {
          return selectedValue.contextAvailable()
        }

        override fun getSeparatorAbove(value: CodeVisionEntry): ListSeparator? {
          return if (value == settingButton) ListSeparator() else null
        }
      }

      return CodeVisionListPopup(project, aStep)
    }
  }
}


