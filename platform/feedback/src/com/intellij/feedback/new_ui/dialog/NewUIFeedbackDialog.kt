// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.new_ui.dialog

import com.intellij.feedback.common.dialog.BlockBasedFeedbackDialogWithEmail
import com.intellij.feedback.common.dialog.CommonFeedbackSystemInfoData
import com.intellij.feedback.common.dialog.JsonSerializable
import com.intellij.feedback.common.dialog.showFeedbackSystemInfoDialog
import com.intellij.feedback.common.dialog.uiBlocks.*
import com.intellij.feedback.new_ui.CancelFeedbackNotification
import com.intellij.feedback.new_ui.bundle.NewUIFeedbackBundle
import com.intellij.feedback.new_ui.state.NewUIInfoService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.NewUi
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import javax.swing.Action
import javax.swing.Action.NAME

class NewUIFeedbackDialog(
  project: Project?,
  forTest: Boolean
) : BlockBasedFeedbackDialogWithEmail<NewUIFeedbackSystemInfoData>(project, forTest) {

  /** Increase the additional number when feedback format is changed */
  override val myFeedbackJsonVersion: Int = super.myFeedbackJsonVersion + 1

  override val zendeskTicketTitle: String = "New UI in-IDE Feedback"
  override val zendeskFeedbackType: String = "New UI in-IDE Feedback"
  override val myFeedbackReportId: String = "new_ui_feedback"

  override val myTitle: String = NewUIFeedbackBundle.message("dialog.top.title")
  override val myBlocks: List<FeedbackBlock> = listOf(
    TopLabelBlock(NewUIFeedbackBundle.message("dialog.title")),
    DescriptionBlock(NewUIFeedbackBundle.message("dialog.description")),
    RatingBlock(NewUIFeedbackBundle.message("dialog.rating.label"), "rating"),
    TextAreaBlock(NewUIFeedbackBundle.message("dialog.like_most.textarea.label"), "like_most"),
    TextAreaBlock(NewUIFeedbackBundle.message("dialog.dislike.textarea.label"), "dislike")
  )

  override val mySystemInfoData: NewUIFeedbackSystemInfoData by lazy {
    val state = NewUIInfoService.getInstance().state
    createNewUIFeedbackSystemInfoData(NewUi.isEnabled(), state.enableNewUIDate, state.disableNewUIDate)
  }
  override val myShowFeedbackSystemInfoDialog: () -> Unit = {
    showNewUIFeedbackSystemInfoDialog(myProject, mySystemInfoData)
  }

  init {
    init()
  }

  override fun doCancelAction() {
    super.doCancelAction()
    CancelFeedbackNotification().notify(myProject)
  }

  override fun getCancelAction(): Action {
    val cancelAction = super.getCancelAction()
    cancelAction.putValue(NAME, NewUIFeedbackBundle.message("dialog.cancel.label"))
    return cancelAction
  }
}

@Serializable
data class NewUIFeedbackSystemInfoData(
  val isNewUINowEnabled: Boolean,
  val enableNewUIDate: LocalDateTime?,
  val disableNewUIDate: LocalDateTime?,
  val commonSystemInfo: CommonFeedbackSystemInfoData
) : JsonSerializable {
  override fun toString(): String {
    return buildString {
      appendLine(NewUIFeedbackBundle.message("dialog.system.info.isNewUIEnabled"))
      appendLine()
      appendLine(if (isNewUINowEnabled) "True" else "False")
      appendLine()
      appendLine(NewUIFeedbackBundle.message("dialog.system.info.enableNewUIDate"))
      appendLine()
      appendLine(enableNewUIDate?.date.toString())
      appendLine()
      appendLine(NewUIFeedbackBundle.message("dialog.system.info.disableNewUIDate"))
      appendLine()
      appendLine(disableNewUIDate?.date.toString())
      appendLine()
      commonSystemInfo.toString()
    }
  }

  override fun serializeToJson(json: Json): JsonElement {
    return json.encodeToJsonElement(this)
  }
}

private fun showNewUIFeedbackSystemInfoDialog(project: Project?,
                                              systemInfoData: NewUIFeedbackSystemInfoData
) = showFeedbackSystemInfoDialog(project, systemInfoData.commonSystemInfo) {
  row(NewUIFeedbackBundle.message("dialog.system.info.isNewUIEnabled")) {
    label(if (systemInfoData.isNewUINowEnabled) "True" else "False") //NON-NLS
  }
  row(NewUIFeedbackBundle.message("dialog.system.info.enableNewUIDate")) {
    label(systemInfoData.enableNewUIDate?.date.toString())
  }
  row(NewUIFeedbackBundle.message("dialog.system.info.disableNewUIDate")) {
    label(systemInfoData.disableNewUIDate?.date.toString())
  }
}

private fun createNewUIFeedbackSystemInfoData(isNewUINowEnabled: Boolean,
                                              enableNewUIDate: LocalDateTime?,
                                              disableNewUIDate: LocalDateTime?): NewUIFeedbackSystemInfoData {
  return NewUIFeedbackSystemInfoData(isNewUINowEnabled, enableNewUIDate, disableNewUIDate,
                                     CommonFeedbackSystemInfoData.getCurrentData())
}