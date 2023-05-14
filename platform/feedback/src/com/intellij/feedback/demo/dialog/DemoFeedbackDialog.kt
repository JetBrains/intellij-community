// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.demo.dialog

import com.intellij.feedback.common.dialog.BlockBasedFeedbackDialog
import com.intellij.feedback.common.dialog.COMMON_FEEDBACK_SYSTEM_INFO_VERSION
import com.intellij.feedback.common.dialog.CommonFeedbackSystemInfoData
import com.intellij.feedback.common.dialog.uiBlocks.*
import com.intellij.feedback.demo.bundle.DemoFeedbackBundle
import com.intellij.openapi.project.Project

class DemoFeedbackDialog(
  project: Project?,
  forTest: Boolean,
) : BlockBasedFeedbackDialog(project, forTest) {

  //TODO: Thinks about updating json version
  /** Increase the additional number when feedback format is changed */
  override val myFeedbackJsonVersion: Int = COMMON_FEEDBACK_SYSTEM_INFO_VERSION + 1
  override val myFeedbackReportId: String = "demo_feedback"

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
      .addOtherTextField(),
    TextAreaBlock(DemoFeedbackBundle.message("dialog.textarea.label"), "textarea")
  )

  override val mySystemInfoData: CommonFeedbackSystemInfoData by lazy {
    CommonFeedbackSystemInfoData.getCurrentData()
  }

  init {
    init()
  }
}