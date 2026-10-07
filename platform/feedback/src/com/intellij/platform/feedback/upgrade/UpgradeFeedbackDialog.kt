// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.upgrade

import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.dialog.CommonBlockBasedFeedbackDialogWithEmail
import com.intellij.platform.feedback.dialog.uiBlocks.CheckBoxGroupBlock
import com.intellij.platform.feedback.dialog.uiBlocks.CheckBoxItemData
import com.intellij.platform.feedback.dialog.uiBlocks.ComboBoxBlock
import com.intellij.platform.feedback.dialog.uiBlocks.ComboBoxItemData
import com.intellij.platform.feedback.dialog.uiBlocks.DescriptionBlock
import com.intellij.platform.feedback.dialog.uiBlocks.FeedbackBlock
import com.intellij.platform.feedback.dialog.uiBlocks.SegmentedButtonBlock
import com.intellij.platform.feedback.dialog.uiBlocks.TextAreaBlock
import com.intellij.platform.feedback.dialog.uiBlocks.TopLabelBlock

internal class UpgradeFeedbackDialog(
  project: Project?,
  forTest: Boolean,
) : CommonBlockBasedFeedbackDialogWithEmail(project, forTest) {

  /** Increase the additional number when the feedback format is changed */
  override val myFeedbackJsonVersion: Int = super.myFeedbackJsonVersion + 1

  override val myFeedbackReportId: String = "upgrade_feedback"
  override val zendeskTicketTitle: String = "IDE Update in-IDE Feedback"
  override val zendeskFeedbackType: String = "IDE Update Feedback"

  private val productName: String = ApplicationNamesInfo.getInstance().fullProductName

  override val myTitle: String = UpgradeFeedbackBundle.message("upgrade.feedback.dialog.top.title")

  override val myBlocks: List<FeedbackBlock> = listOf(
    TopLabelBlock(UpgradeFeedbackBundle.message("upgrade.feedback.dialog.title")),
    DescriptionBlock(UpgradeFeedbackBundle.message("upgrade.feedback.dialog.description", productName)),

    SegmentedButtonBlock(
      UpgradeFeedbackBundle.message("upgrade.feedback.dialog.ease.label", productName),
      List(5) { (it + 1).toString() },
      "update_ease",
    )
      .addLeftBottomLabel(UpgradeFeedbackBundle.message("upgrade.feedback.dialog.ease.left.hint"))
      .addRightBottomLabel(UpgradeFeedbackBundle.message("upgrade.feedback.dialog.ease.right.hint")),

    ComboBoxBlock(
      UpgradeFeedbackBundle.message("upgrade.feedback.dialog.button.label"),
      listOf(
        ComboBoxItemData(UpgradeFeedbackBundle.message("upgrade.feedback.dialog.button.easy.to.miss"), "easy_to_miss"),
        ComboBoxItemData(UpgradeFeedbackBundle.message("upgrade.feedback.dialog.button.just.right"), "just_right"),
        ComboBoxItemData(UpgradeFeedbackBundle.message("upgrade.feedback.dialog.button.bit.distracting"), "a_bit_distracting"),
        ComboBoxItemData(UpgradeFeedbackBundle.message("upgrade.feedback.dialog.button.too.distracting"), "too_distracting"),
      ),
      "update_button",
    ),

    CheckBoxGroupBlock(
      UpgradeFeedbackBundle.message("upgrade.feedback.dialog.problems.label"),
      listOf(
        CheckBoxItemData(UpgradeFeedbackBundle.message("upgrade.feedback.dialog.problems.download"), "download_slow_or_failed"),
        CheckBoxItemData(UpgradeFeedbackBundle.message("upgrade.feedback.dialog.problems.reinstall"), "manual_reinstall"),
        CheckBoxItemData(UpgradeFeedbackBundle.message("upgrade.feedback.dialog.problems.plugins"), "plugins_disabled"),
        CheckBoxItemData(UpgradeFeedbackBundle.message("upgrade.feedback.dialog.problems.settings"), "settings_changed"),
        CheckBoxItemData(UpgradeFeedbackBundle.message("upgrade.feedback.dialog.problems.restart"), "restart_or_indexing_slow"),
        CheckBoxItemData(UpgradeFeedbackBundle.message("upgrade.feedback.dialog.problems.unclear"), "unclear_process"),
        CheckBoxItemData(UpgradeFeedbackBundle.message("upgrade.feedback.dialog.problems.none"), "no_problems"),
      ),
      "update_problems",
    ).addOtherTextField(),

    TextAreaBlock(UpgradeFeedbackBundle.message("upgrade.feedback.dialog.improvements.label"), "improvements"),
  )

  init {
    init()
  }
}
