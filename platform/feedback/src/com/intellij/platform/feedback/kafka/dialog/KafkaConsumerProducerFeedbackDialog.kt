// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.kafka.dialog

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialogWithEmail
import com.intellij.platform.feedback.dialog.CommonFeedbackSystemData
import com.intellij.platform.feedback.dialog.showFeedbackSystemInfoDialog
import com.intellij.platform.feedback.dialog.uiBlocks.*
import com.intellij.platform.feedback.impl.notification.ThanksForFeedbackNotification
import com.intellij.platform.feedback.kafka.bundle.KafkaFeedbackBundle
import javax.swing.Action

abstract class KafkaConsumerProducerFeedbackDialog(
  project: Project?,
  forTest: Boolean,
  @NlsContexts.Label questionLabel: String
) : BlockBasedFeedbackDialogWithEmail<CommonFeedbackSystemData>(project, forTest) {

  /** Increase the additional number when feedback format is changed */
  override val myFeedbackJsonVersion: Int = super.myFeedbackJsonVersion + 2

  override val zendeskTicketTitle: String = "Kafka in-IDE Feedback"
  override val zendeskFeedbackType: String = "Kafka Consumer in-IDE Feedback"
  override val myFeedbackReportId: String = "kafka_consumer_feedback"

  override val myTitle: String = KafkaFeedbackBundle.message("dialog.top.title")
  private val featureSource: List<String> = listOf("blog_post", "product_documentation", "saw_button", "saw_spring_gutter")
  private val goalOfUsing: List<String> = listOf("debug_streaming", "debug_cluster", "view_cluster")


  override val myBlocks: List<FeedbackBlock> = listOf(
    TopLabelBlock(KafkaFeedbackBundle.message("dialog.title")),
    DescriptionBlock(questionLabel),
    CheckBoxGroupBlock(
      KafkaFeedbackBundle.message("question.find.feature.label"),
      listOf(
        CheckBoxItemData(
          KafkaFeedbackBundle.message("find.feature.0.label"),
          featureSource[0]
        ),
        CheckBoxItemData(
          KafkaFeedbackBundle.message("find.feature.1.label"),
          featureSource[1]
        ),
        CheckBoxItemData(
          KafkaFeedbackBundle.message("find.feature.2.label"),
          featureSource[2]
        ),
        CheckBoxItemData(
          KafkaFeedbackBundle.message("find.feature.3.label"),
          featureSource[3]
        )
      ),
      "find_about_source"
    ).addOtherTextField(),
    CheckBoxGroupBlock(
      KafkaFeedbackBundle.message("using.functionality"),
      listOf(
        CheckBoxItemData(
          KafkaFeedbackBundle.message("using.functionality.0.label"),
          goalOfUsing[0]
        ),
        CheckBoxItemData(
          KafkaFeedbackBundle.message("using.functionality.1.label"),
          goalOfUsing[1]
        ),
        CheckBoxItemData(
          KafkaFeedbackBundle.message("using.functionality.2.label"),
          goalOfUsing[2]
        ),
      ),
      "goal_of_use"
    ).addOtherTextField(),
    TextAreaBlock(KafkaFeedbackBundle.message("any.feedback.label"), "textarea"),
  )

  override val mySystemInfoData: CommonFeedbackSystemData by lazy {
    CommonFeedbackSystemData.getCurrentData()
  }
  override val myShowFeedbackSystemInfoDialog: () -> Unit = {
    showFeedbackSystemInfoDialog(myProject, mySystemInfoData)
  }


  init {
    @Suppress("LeakingThis")
    init()
  }

  override fun showThanksNotification() {
    ThanksForFeedbackNotification(description = KafkaFeedbackBundle.message(
      "notification.thanks.feedback.content")).notify(myProject)
  }

  override fun getCancelAction(): Action {
    val cancelAction = super.getCancelAction()
    cancelAction.putValue(Action.NAME, KafkaFeedbackBundle.message("new.user.dialog.cancel.label"))
    return cancelAction
  }
}