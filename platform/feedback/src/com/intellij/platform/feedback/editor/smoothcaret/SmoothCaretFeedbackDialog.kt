// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.editor.smoothcaret

import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialogWithEmail
import com.intellij.platform.feedback.dialog.CommonFeedbackSystemData
import com.intellij.platform.feedback.dialog.showFeedbackSystemInfoDialog
import com.intellij.platform.feedback.dialog.uiBlocks.DescriptionBlock
import com.intellij.platform.feedback.dialog.uiBlocks.FeedbackBlock
import com.intellij.platform.feedback.dialog.uiBlocks.RadioButtonGroupBlock
import com.intellij.platform.feedback.dialog.uiBlocks.RadioButtonItemData
import com.intellij.platform.feedback.dialog.uiBlocks.RatingBlock
import com.intellij.platform.feedback.dialog.uiBlocks.TextAreaBlock
import com.intellij.platform.feedback.dialog.uiBlocks.TopLabelBlock


class SmoothCaretFeedbackDialog(
  project: Project,
  forTest: Boolean,
) : BlockBasedFeedbackDialogWithEmail<SmoothCaretUsageData>(project, forTest) {

  override val zendeskTicketTitle: String = "Smooth Caret Feedback"

  override val zendeskFeedbackType: String = "Smooth Caret Feedback"

  override val myFeedbackReportId: String = "smooth_caret"

  override val myTitle: String = SmoothCaretFeedbackBundle.message("feedback.smooth.caret.dialog.title")

  override suspend fun computeSystemInfoData(): SmoothCaretUsageData {
    val editorSettings = EditorSettingsExternalizable.getInstance()
    return SmoothCaretUsageData(
      isAnimatedCaret = editorSettings.isSmoothCaretMovement,
      caretEasing = editorSettings.caretEasing.name,
      smoothCaretBlinking = editorSettings.isSmoothBlinkCaret,
      systemInfo = CommonFeedbackSystemData.getCurrentData(),
    )
  }

  override fun showFeedbackSystemInfoDialog(systemInfoData: SmoothCaretUsageData) {
    showFeedbackSystemInfoDialog(myProject, systemInfoData.systemInfo) {
      row(SmoothCaretFeedbackBundle.message("feedback.smooth.caret.system.info.is.animated")) {
        label(systemInfoData.isAnimatedCaret.toString())
      }
      row(SmoothCaretFeedbackBundle.message("feedback.smooth.caret.system.info.easing")) {
        label(systemInfoData.caretEasing)
      }
      row(SmoothCaretFeedbackBundle.message("feedback.smooth.caret.system.info.blinking")) {
        label(systemInfoData.smoothCaretBlinking.toString())
      }
    }
  }

  override val myBlocks: List<FeedbackBlock> = listOf(
    TopLabelBlock(SmoothCaretFeedbackBundle.message("feedback.smooth.caret.dialog.header")),
    DescriptionBlock(SmoothCaretFeedbackBundle.message("feedback.smooth.caret.dialog.description")),
    
    RadioButtonGroupBlock(
      SmoothCaretFeedbackBundle.message("feedback.smooth.caret.dialog.noticed.question"),
      listOf(
        RadioButtonItemData(SmoothCaretFeedbackBundle.message("feedback.smooth.caret.dialog.noticed.yes"), "yes"),
        RadioButtonItemData(SmoothCaretFeedbackBundle.message("feedback.smooth.caret.dialog.noticed.no"), "no"),
      ),
      "noticed_animated_caret",
    ).requireAnswer(),
    
    RatingBlock(
      SmoothCaretFeedbackBundle.message("feedback.smooth.caret.dialog.rating.title"),
      "smooth_caret_rating",
    ),
    
    TextAreaBlock(
      SmoothCaretFeedbackBundle.message("feedback.smooth.caret.dialog.additional"),
      "additional_feedback",
    )
      .setPlaceholder(SmoothCaretFeedbackBundle.message("feedback.smooth.caret.dialog.additional.placeholder")),
  )

  init {
    init()
  }
}
