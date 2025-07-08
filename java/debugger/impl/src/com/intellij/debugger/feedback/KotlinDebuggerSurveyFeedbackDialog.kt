// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.feedback

import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialog
import com.intellij.platform.feedback.dialog.CommonFeedbackSystemData
import com.intellij.platform.feedback.dialog.SystemDataJsonSerializable
import com.intellij.platform.feedback.dialog.showFeedbackSystemInfoDialog
import com.intellij.platform.feedback.dialog.uiBlocks.*
import com.intellij.platform.feedback.impl.notification.ThanksForFeedbackNotification
import com.intellij.ui.dsl.builder.BottomGap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

internal class KotlinDebuggerSurveyFeedbackDialog(project: Project?,
                                                  forTest: Boolean) : BlockBasedFeedbackDialog<KotlinDebuggerSystemData>(project, forTest) {

  /** Increase the additional number when feedback format is changed */
  override val myFeedbackJsonVersion: Int = super.myFeedbackJsonVersion + 2

  override val myFeedbackReportId: String = "kotlin_debugger_feedback_survey"

  override val myTitle: String = KotlinDebuggerFeedbackSurveyBundle.message("kotlin.debugger.feedback.dialog.top.title")

  private val ratingJsonElementName = "rating"
  private val experienceJsonElementName = "experience"
  private val experienceBetterThanBeforeJsonElementName = "better_than_before"
  private val experienceSameJsonElementName = "about_the_same"
  private val experienceWorseThanBeforeJsonElementName = "worse_than_before"
  private val experienceNoBeforeJsonElementName = "no_experience_before"

  private val fullApplicationName = ApplicationNamesInfo.getInstance().fullProductName

  private val tellUsMoreJsonElementName = "tell_us_more"


  override val myBlocks: List<FeedbackBlock> = mutableListOf<FeedbackBlock>().apply {
    add(TopLabelBlock(KotlinDebuggerFeedbackSurveyBundle.message("kotlin.debugger.feedback.dialog.title")).setBottomGap(BottomGap.MEDIUM))
    add(
      SegmentedButtonBlock(
        KotlinDebuggerFeedbackSurveyBundle.message("kotlin.debugger.feedback.question.1.title",
                                                   fullApplicationName),
        List(5) { (it + 1).toString() },
        ratingJsonElementName
      )
    )
    add(
      RadioButtonGroupBlock(
        KotlinDebuggerFeedbackSurveyBundle.message("kotlin.debugger.feedback.question.2.title",
                                                   fullApplicationName),
        listOf(
          RadioButtonItemData(
            KotlinDebuggerFeedbackSurveyBundle.message("kotlin.debugger.feedback.question.2.option.1"),
            experienceBetterThanBeforeJsonElementName
          ),
          RadioButtonItemData(
            KotlinDebuggerFeedbackSurveyBundle.message("kotlin.debugger.feedback.question.2.option.2"),
            experienceSameJsonElementName
          ),
          RadioButtonItemData(
            KotlinDebuggerFeedbackSurveyBundle.message("kotlin.debugger.feedback.question.2.option.3"),
            experienceWorseThanBeforeJsonElementName
          ),
          RadioButtonItemData(
            KotlinDebuggerFeedbackSurveyBundle.message("kotlin.debugger.feedback.question.2.option.4", fullApplicationName),
            experienceNoBeforeJsonElementName
          )
        ),
        experienceJsonElementName
      ).requireAnswer()
    )
    add(
      TextAreaBlock(
        KotlinDebuggerFeedbackSurveyBundle.message("kotlin.debugger.feedback.question.3.title"),
        tellUsMoreJsonElementName
      )
    )
  }

  override suspend fun computeSystemInfoData(): KotlinDebuggerSystemData {
    return KotlinDebuggerSystemData(UsageTracker.kotlinDebuggedTimes(), CommonFeedbackSystemData.getCurrentData())
  }

  override fun showFeedbackSystemInfoDialog(systemInfoData: KotlinDebuggerSystemData) {
    showNewUIFeedbackSystemInfoDialog(myProject, systemInfoData)
  }

  init {
    init()
  }

  override fun showThanksNotification() {
    ThanksForFeedbackNotification(
      description = KotlinDebuggerFeedbackSurveyBundle.message("kotlin.debugger.notification.thanks.feedback.content")
    ).notify(myProject)
  }

  private fun showNewUIFeedbackSystemInfoDialog(
    project: Project?,
    systemInfoData: KotlinDebuggerSystemData
  ) = showFeedbackSystemInfoDialog(project, systemInfoData.systemInfo) {
    row(KotlinDebuggerFeedbackSurveyBundle.message("kotlin.debugger.feedback.system.info.number.debugger.usages")) {
      label(systemInfoData.numberDebuggerUsages.toString()) //NON-NLS
    }
  }
}

@Serializable
internal data class KotlinDebuggerSystemData(
  val numberDebuggerUsages: Int,
  val systemInfo: CommonFeedbackSystemData
) : SystemDataJsonSerializable {
  override fun serializeToJson(json: Json): JsonElement {
    return json.encodeToJsonElement(this)
  }

  override fun toString(): String = buildString {
    appendLine(KotlinDebuggerFeedbackSurveyBundle.message("kotlin.debugger.feedback.system.info.number.debugger.usages"))
    appendLine(numberDebuggerUsages)
    append(systemInfo.toString())
  }
}