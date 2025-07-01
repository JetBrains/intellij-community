// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.csat

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialogWithEmail
import com.intellij.platform.feedback.dialog.CommonFeedbackSystemData
import com.intellij.platform.feedback.dialog.SystemDataJsonSerializable
import com.intellij.platform.feedback.dialog.showFeedbackSystemInfoDialog
import com.intellij.platform.feedback.dialog.uiBlocks.FeedbackBlock
import com.intellij.platform.feedback.dialog.uiBlocks.SegmentedButtonBlock
import com.intellij.platform.feedback.dialog.uiBlocks.TextAreaBlock
import com.intellij.platform.feedback.dialog.uiBlocks.TopLabelBlock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

@Serializable
internal data class CsatFeedbackSystemData(
  val isNewUser: Boolean,
  val systemInfo: CommonFeedbackSystemData
): SystemDataJsonSerializable {
  override fun serializeToJson(json: Json): JsonElement {
    return json.encodeToJsonElement(this)
  }

  override fun toString(): String = buildString {
    appendLine("Is new installation:")
    appendLine(isNewUser)
    append(systemInfo.toString())
  }
}

internal class CsatFeedbackDialog(
  project: Project?,
  forTest: Boolean,
) : BlockBasedFeedbackDialogWithEmail<CsatFeedbackSystemData>(project, forTest) {

  /** Increase the additional number when the feedback format is changed */
  override val myFeedbackJsonVersion: Int = super.myFeedbackJsonVersion + 1
  override val zendeskTicketTitle: String = "Experience with IDE"
  override val zendeskFeedbackType: String = "Experience with IDE"

  override val myFeedbackReportId: String = "csat_feedback"

  override suspend fun computeSystemInfoData(): CsatFeedbackSystemData = getCsatSystemInfo()

  @Suppress("HardCodedStringLiteral")
  override fun showFeedbackSystemInfoDialog(systemInfoData: CsatFeedbackSystemData) {
    showFeedbackSystemInfoDialog(myProject, systemInfoData.systemInfo) {
      row("Is new installation:") {
        label(systemInfoData.isNewUser.toString())
      }
    }
  }

  override val myTitle: String = CsatFeedbackBundle.message("dialog.title")

  override val myBlocks: List<FeedbackBlock> = listOf(
    TopLabelBlock(CsatFeedbackBundle.message("dialog.subtitle")),
    SegmentedButtonBlock(CsatFeedbackBundle.message("dialog.rating.label", ApplicationInfo.getInstance().versionName),
                         List(5) { (it + 1).toString() },
                         "csat_rating",
                         listOf(
                           AllIcons.Survey.VeryDissatisfied,
                           AllIcons.Survey.Dissatisfied,
                           AllIcons.Survey.Neutral,
                           AllIcons.Survey.Satisfied,
                           AllIcons.Survey.VerySatisfied
                         ))
      .addLeftBottomLabel(CsatFeedbackBundle.message("dialog.rating.leftHint"))
      .addMiddleBottomLabel(CsatFeedbackBundle.message("dialog.rating.middleHint"))
      .addRightBottomLabel(CsatFeedbackBundle.message("dialog.rating.rightHint")),

    TextAreaBlock(CsatFeedbackBundle.message("dialog.extra.label"), "textarea")
  )

  init {
    init()
  }
}

private fun getCsatSystemInfo(): CsatFeedbackSystemData {
  val userCreatedDate = getCsatUserCreatedDate()
  val today = getCsatToday()
  val isNewUser = userCreatedDate?.let { isNewUser(today, userCreatedDate) } ?: false

  return CsatFeedbackSystemData(isNewUser, CommonFeedbackSystemData.getCurrentData())
}