// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.pycharmUi.dialog

import com.intellij.feedback.common.dialog.BlockBasedFeedbackDialogWithEmail
import com.intellij.feedback.common.dialog.CommonFeedbackSystemInfoData
import com.intellij.feedback.common.dialog.JsonSerializable
import com.intellij.feedback.common.dialog.showFeedbackSystemInfoDialog
import com.intellij.feedback.common.dialog.uiBlocks.*
import com.intellij.feedback.common.notification.ThanksForFeedbackNotification
import com.intellij.feedback.pycharmUi.bundle.PyCharmUIFeedbackBundle
import com.intellij.openapi.project.Project
import com.intellij.ui.NewUI
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import javax.swing.Action
import javax.swing.Action.NAME

class PyCharmUIFeedbackDialog(
  project: Project?,
  forTest: Boolean
) : BlockBasedFeedbackDialogWithEmail<PyCharmFeedbackSystemInfoData>(project, forTest) {

  override val myFeedbackJsonVersion: Int = super.myFeedbackJsonVersion + 1
  override val zendeskTicketTitle: String = "PyCharm in-IDE Feedback"
  override val zendeskFeedbackType: String = "PyCharm in-IDE Feedback"
  override val myFeedbackReportId: String = "pycharm_feedback"
  override val myTitle: String = PyCharmUIFeedbackBundle.message("dialog.top.title")
  override val myBlocks: List<FeedbackBlock> = listOf(
    TopLabelBlock(PyCharmUIFeedbackBundle.message("dialog.title")),
    DescriptionBlock(PyCharmUIFeedbackBundle.message("dialog.description")),
    RatingBlock(PyCharmUIFeedbackBundle.message("dialog.rating.overall.impression.label"), "overall_impression"),
    RatingBlock(PyCharmUIFeedbackBundle.message("dialog.rating.ui.impression.label"),
                "ui_impression"),
    TextAreaBlock(PyCharmUIFeedbackBundle.message("dialog.like_most.textarea.label"), "like_most"),
    TextAreaBlock(PyCharmUIFeedbackBundle.message("dialog.dislike.textarea.label"), "dislike_most"),
  )
  override val mySystemInfoData: PyCharmFeedbackSystemInfoData by lazy {
    createPyCharmFeedbackSystemInfoData(NewUI.isEnabled())
  }
  override val myShowFeedbackSystemInfoDialog: () -> Unit = {
    showPyCharmFeedbackSystemInfoDialog(myProject, mySystemInfoData)
  }

  init {
    init()
  }

  override fun showThanksNotification() {
    ThanksForFeedbackNotification(description = PyCharmUIFeedbackBundle.message(
      "notification.thanks.feedback.content")).notify(myProject)
  }

  override fun getCancelAction(): Action {
    val cancelAction = super.getCancelAction()
    cancelAction.putValue(NAME, PyCharmUIFeedbackBundle.message("dialog.cancel.label"))
    return cancelAction
  }
}

@Serializable
data class PyCharmFeedbackSystemInfoData(
  val isNewUINowEnabled: Boolean,
  val commonSystemInfo: CommonFeedbackSystemInfoData
) : JsonSerializable {
  override fun toString(): String {
    return buildString {
      appendLine(PyCharmUIFeedbackBundle.message("dialog.system.info.isNewUIEnabled"))
      appendLine()
      appendLine(if (isNewUINowEnabled) "True" else "False")
      appendLine()
      commonSystemInfo.toString()
    }
  }

  override fun serializeToJson(json: Json): JsonElement {
    return json.encodeToJsonElement(this)
  }
}

private fun showPyCharmFeedbackSystemInfoDialog(project: Project?,
                                                systemInfoData: PyCharmFeedbackSystemInfoData
) = showFeedbackSystemInfoDialog(project, systemInfoData.commonSystemInfo) {
  row(PyCharmUIFeedbackBundle.message("dialog.system.info.isNewUIEnabled")) {
    label(if (systemInfoData.isNewUINowEnabled) "True" else "False") //NON-NLS
  }
}

private fun createPyCharmFeedbackSystemInfoData(isNewUINowEnabled: Boolean): PyCharmFeedbackSystemInfoData {
  return PyCharmFeedbackSystemInfoData(isNewUINowEnabled, CommonFeedbackSystemInfoData.getCurrentData())
}