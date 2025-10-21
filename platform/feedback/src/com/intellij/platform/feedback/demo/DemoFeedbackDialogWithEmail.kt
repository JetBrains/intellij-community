// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.demo

import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialogWithEmail
import com.intellij.platform.feedback.dialog.CommonFeedbackSystemData
import com.intellij.platform.feedback.dialog.showFeedbackSystemInfoDialog
import com.intellij.platform.feedback.dialog.uiBlocks.*
import kotlinx.serialization.json.JsonObject

class DemoFeedbackDialogWithEmail(
  project: Project?,
  forTest: Boolean,
) : BlockBasedFeedbackDialogWithEmail<CommonFeedbackSystemData>(project, forTest) {

  /** Increase the additional number when feedback format is changed */
  override val myFeedbackJsonVersion: Int = super.myFeedbackJsonVersion + 1
  override val myFeedbackReportId: String = "demo_feedback_with_email"

  override val zendeskFeedbackType: String = "demo_feedback_with_email"
  override val zendeskTicketTitle: String = "Demo Feedback Survey"

  override suspend fun computeSystemInfoData(): CommonFeedbackSystemData = CommonFeedbackSystemData.getCurrentData()

  override fun showFeedbackSystemInfoDialog(systemInfoData: CommonFeedbackSystemData) {
    showFeedbackSystemInfoDialog(myProject, systemInfoData)
  }

  override val myTitle: String = DemoFeedbackBundle.message("dialog.top.title")
  override val myBlocks: List<FeedbackBlock> = listOf(
    TopLabelBlock(DemoFeedbackBundle.message("dialog.title")),
    DescriptionBlock(DemoFeedbackBundle.message("dialog.description")),
    RatingBlock(DemoFeedbackBundle.message("dialog.rating.label"), "rating"),
    SegmentedButtonBlock(DemoFeedbackBundle.message("dialog.segmentedButton.label"),
                         List(8) { (it + 1).toString() },
                         "segmented_button")
      .addLeftBottomLabel(DemoFeedbackBundle.message("dialog.segmentedButton.leftHint"))
      .addMiddleBottomLabel(DemoFeedbackBundle.message("dialog.segmentedButton.middleHint"))
      .addRightBottomLabel(DemoFeedbackBundle.message("dialog.segmentedButton.rightHint")),
    ComboBoxBlock(DemoFeedbackBundle.message("dialog.combobox.label"),
                  List(3) { DemoFeedbackBundle.message("dialog.combobox.item.${it}") },
                  "combobox")
      .addComment(DemoFeedbackBundle.message("dialog.combobox.comment"))
      .setColumnSize(10),
    CheckBoxGroupBlock(DemoFeedbackBundle.message("dialog.checkbox.group.label"),
                       List(3) {
                         CheckBoxItemData(DemoFeedbackBundle.message("dialog.checkbox.item.${it}.label"),
                                          "checkbox_${it}")
                       }, "checkbox_group")
      .addOtherTextField().requireAnswer(),
    TextAreaBlock(DemoFeedbackBundle.message("dialog.textarea.label"), "textarea")
  )

  init {
    init()
  }

  override fun computeZendeskTicketTags(collectedData: JsonObject): List<String> {
    return listOf("demo_tag_1", "demo_tag_2")
  }

  override fun shouldAutoCloseZendeskTicket(): Boolean {
    return true
  }
}