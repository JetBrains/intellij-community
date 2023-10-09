// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.pluginPage

import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialog
import com.intellij.platform.feedback.dialog.CommonFeedbackSystemData
import com.intellij.platform.feedback.dialog.showFeedbackSystemInfoDialog
import com.intellij.platform.feedback.dialog.uiBlocks.*
import java.util.*

enum class CaseType {
  DISABLE, UNINSTALL
}

internal abstract class PluginPageFeedbackDialog(pluginName: String, caseType: CaseType, project: Project?, forTest: Boolean) :
  BlockBasedFeedbackDialog<CommonFeedbackSystemData>(project, forTest) {


  private val pluginNameCapitalized = pluginName.replaceFirstChar {
    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
  }

  private val messageIdPrefixForCase = caseType.name.lowercase()

  override val myFeedbackJsonVersion: Int = super.myFeedbackJsonVersion + 1
  override val myTitle: String = PluginPageFeedbackBundle.message("dialog.top.title")
  override val mySystemInfoData: CommonFeedbackSystemData by lazy {
    CommonFeedbackSystemData.getCurrentData()
  }
  override val myShowFeedbackSystemInfoDialog: () -> Unit = {
    showFeedbackSystemInfoDialog(myProject, mySystemInfoData)
  }

  private val reasonsItems: List<CheckBoxItemData> = listOf(
    CheckBoxItemData(PluginPageFeedbackBundle.message("dialog.checkbox.group.option.1.label"),
                     "installed_by_accident"),
    CheckBoxItemData(PluginPageFeedbackBundle.message("dialog.checkbox.group.option.2.label"),
                     "missing_features"),
    CheckBoxItemData(PluginPageFeedbackBundle.message("dialog.checkbox.group.option.3.label"),
                     "serious_bugs"),
  )

  override val myBlocks: List<FeedbackBlock> = listOf(
    TopLabelBlock(PluginPageFeedbackBundle.message("dialog.title", pluginNameCapitalized)),
    CheckBoxGroupBlock(PluginPageFeedbackBundle.message("$messageIdPrefixForCase.dialog.checkbox.group.label"),
                       reasonsItems, "reasons").addOtherTextField().requireAnswer(),
    TextAreaBlock(PluginPageFeedbackBundle.message("dialog.textarea.label"), "what_to_improve")
  )

}