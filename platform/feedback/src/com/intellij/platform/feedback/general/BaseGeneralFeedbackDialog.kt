// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.general

import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialogWithEmail
import com.intellij.platform.feedback.dialog.CommonFeedbackSystemData
import com.intellij.platform.feedback.dialog.showFeedbackSystemInfoDialog
import com.intellij.platform.feedback.dialog.uiBlocks.*
import com.intellij.platform.feedback.impl.notification.ThanksForFeedbackNotification
import javax.swing.Action

internal abstract class BaseGeneralFeedbackDialog(@NlsContexts.Label descriptionBlockMessage: String?,
                                         project: Project?,
                                         forTest: Boolean
) : BlockBasedFeedbackDialogWithEmail<CommonFeedbackSystemData>(project, forTest) {

  /** Increase the additional number when feedback format is changed */
  override val myFeedbackJsonVersion: Int = super.myFeedbackJsonVersion + 1

  protected val interfaceJsonElementName = "interface"
  protected val priceJsonElementName = "price"
  protected val stabilityJsonElementName = "stability"
  protected val featureSetJsonElementName = "feature_set"
  protected val performanceJsonElementName = "performance"
  private val tellUsMoreJsonElementName = "tell_us_more"

  private val ratingItems: List<RatingItem> = listOf(
    RatingItem(BaseGeneralFeedbackBundle.message("base.general.dialog.rating.block.label.1"), interfaceJsonElementName),
    RatingItem(BaseGeneralFeedbackBundle.message("base.general.dialog.rating.block.label.2"), priceJsonElementName),
    RatingItem(BaseGeneralFeedbackBundle.message("base.general.dialog.rating.block.label.3"), stabilityJsonElementName),
    RatingItem(BaseGeneralFeedbackBundle.message("base.general.dialog.rating.block.label.4"), featureSetJsonElementName),
    RatingItem(BaseGeneralFeedbackBundle.message("base.general.dialog.rating.block.label.5"), performanceJsonElementName)
  )

  override val myBlocks: List<FeedbackBlock> = mutableListOf<FeedbackBlock>().apply {
    this.add(TopLabelBlock(BaseGeneralFeedbackBundle.message("base.general.dialog.title")))
    if (descriptionBlockMessage != null) {
      this.add(DescriptionBlock(descriptionBlockMessage))
    }
    this.add(RatingGroupBlock(BaseGeneralFeedbackBundle.message("base.general.dialog.rating.block.top.label"), ratingItems)
               .setHint(BaseGeneralFeedbackBundle.message("base.general.dialog.rating.block.hint")).setRandomOrder(true))
    this.add(TextAreaBlock(BaseGeneralFeedbackBundle.message("base.general.dialog.text.area.details"), tellUsMoreJsonElementName))
  }

  override val mySystemInfoData: CommonFeedbackSystemData by lazy {
    CommonFeedbackSystemData.getCurrentData()
  }
  override val myShowFeedbackSystemInfoDialog: () -> Unit = {
    showFeedbackSystemInfoDialog(myProject, mySystemInfoData)
  }

  override fun showThanksNotification() {
    ThanksForFeedbackNotification(description = BaseGeneralFeedbackBundle.message(
      "base.general.notification.thanks.feedback.content", ApplicationNamesInfo.getInstance().fullProductName)).notify(myProject)
  }

  override fun getCancelAction(): Action {
    val cancelAction = super.getCancelAction()
    cancelAction.putValue(Action.NAME, BaseGeneralFeedbackBundle.message("base.general.dialog.cancel.label"))
    return cancelAction
  }
}