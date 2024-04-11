// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.twnames

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialogWithEmail
import com.intellij.platform.feedback.dialog.CommonFeedbackSystemData
import com.intellij.platform.feedback.dialog.SystemDataJsonSerializable
import com.intellij.platform.feedback.dialog.showFeedbackSystemInfoDialog
import com.intellij.platform.feedback.dialog.uiBlocks.*
import com.intellij.platform.feedback.impl.notification.ThanksForFeedbackNotification
import com.intellij.ui.NewUI
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

internal class TwNamesFeedbackDialog(
  project: Project,
  forTest: Boolean
) : BlockBasedFeedbackDialogWithEmail<TwNamesFeedbackSystemData>(project, forTest) {

  override val myFeedbackJsonVersion: Int = super.myFeedbackJsonVersion + 1

  override val zendeskTicketTitle: String = "${ApplicationNamesInfo.getInstance().fullProductName} Tool Window Names Feedback"
  override val zendeskFeedbackType: String = "Tool Window Names Feedback"

  override val myFeedbackReportId: String = "tw_names_feedback"
  override val myTitle: String = TwNamesFeedbackMessagesBundle.message("tw.names.dialog.top.title")

  override val myBlocks: List<FeedbackBlock> = listOf(
    TopLabelBlock(TwNamesFeedbackMessagesBundle.message("tw.names.dialog.title")),
    DescriptionBlock(TwNamesFeedbackMessagesBundle.message("tw.names.dialog.description")),

    RatingBlock(TwNamesFeedbackMessagesBundle.message("tw.names.dialog.rating.label"), "rating"),
    TextAreaBlock(TwNamesFeedbackMessagesBundle.message("tw.names.dialog.like.label"), "like_feedback"),
    TextAreaBlock(TwNamesFeedbackMessagesBundle.message("tw.names.dialog.dislike.label"), "dislike_feedback")
  )

  override val mySystemInfoData: TwNamesFeedbackSystemData by lazy {
    val layout = mutableMapOf<ToolWindowAnchor, Int>()
    layout[ToolWindowAnchor.TOP] = 0
    layout[ToolWindowAnchor.BOTTOM] = 0
    layout[ToolWindowAnchor.LEFT] = 0
    layout[ToolWindowAnchor.RIGHT] = 0

    val toolWindowManagerEx = ToolWindowManagerEx.getInstanceEx(project)
    val x = toolWindowManagerEx.getLayout()
    for (info in x.getInfos().values) {
      val toolWindow = toolWindowManagerEx.getToolWindow(info.id)
      if (toolWindow != null && info.isShowStripeButton && toolWindow.isAvailable) {
        layout[info.anchor] = layout[info.anchor]!! + 1
      }
    }

    createFeedbackSystemInfoData(UISettings.getInstance().showToolWindowsNames, NewUI.isEnabled(), layout)
  }

  override val myShowFeedbackSystemInfoDialog: () -> Unit = {
    showFeedbackSystemInfoDialog(myProject, mySystemInfoData)
  }

  init {
    init()
  }

  override fun showThanksNotification() {
    ThanksForFeedbackNotification(
      description = TwNamesFeedbackMessagesBundle.message("tw.names.notification.thanks.feedback.content",
                                                          ApplicationNamesInfo.getInstance().fullProductName)
    ).notify(myProject)
  }
}

@Serializable
internal data class TwNamesFeedbackSystemData(
  val isNewUiEnabled: Boolean,
  val isToolWindowNamesEnabled: Boolean,
  val top: Int,
  val left: Int,
  val bottom: Int,
  val right: Int,
  val commonSystemInfo: CommonFeedbackSystemData
) : SystemDataJsonSerializable {
  override fun toString(): String {
    return buildString {
      appendLine(TwNamesFeedbackMessagesBundle.message("dialog.system.info.isNewUiEnabled"))
      appendLine()
      appendLine(if (isNewUiEnabled) "True" else "False")
      appendLine()
      appendLine(TwNamesFeedbackMessagesBundle.message("dialog.system.info.isToolWindowNamesEnabled"))
      appendLine()
      appendLine(if (isToolWindowNamesEnabled) "True" else "False")
      appendLine()
      appendLine(TwNamesFeedbackMessagesBundle.message("dialog.system.info.top"))
      appendLine()
      appendLine(top)
      appendLine()
      appendLine(TwNamesFeedbackMessagesBundle.message("dialog.system.info.left"))
      appendLine()
      appendLine(left)
      appendLine()
      appendLine(TwNamesFeedbackMessagesBundle.message("dialog.system.info.bottom"))
      appendLine()
      appendLine(bottom)
      appendLine()
      appendLine(TwNamesFeedbackMessagesBundle.message("dialog.system.info.right"))
      appendLine()
      appendLine(right)
      appendLine()
      commonSystemInfo.toString()
    }
  }

  override fun serializeToJson(json: Json): JsonElement {
    return json.encodeToJsonElement(this)
  }
}

private fun showFeedbackSystemInfoDialog(project: Project?, systemInfoData: TwNamesFeedbackSystemData) {
  showFeedbackSystemInfoDialog(project, systemInfoData.commonSystemInfo) {
    row(TwNamesFeedbackMessagesBundle.message("dialog.system.info.isNewUiEnabled")) {
      label(if (systemInfoData.isNewUiEnabled) "True" else "False") //NON-NLS
    }
    row(TwNamesFeedbackMessagesBundle.message("dialog.system.info.isToolWindowNamesEnabled")) {
      label(if (systemInfoData.isToolWindowNamesEnabled) "True" else "False") //NON-NLS
    }
    row(TwNamesFeedbackMessagesBundle.message("dialog.system.info.top")) {
      label(systemInfoData.top.toString())
    }
    row(TwNamesFeedbackMessagesBundle.message("dialog.system.info.left")) {
      label(systemInfoData.left.toString())
    }
    row(TwNamesFeedbackMessagesBundle.message("dialog.system.info.bottom")) {
      label(systemInfoData.bottom.toString())
    }
    row(TwNamesFeedbackMessagesBundle.message("dialog.system.info.right")) {
      label(systemInfoData.right.toString())
    }
  }
}

private fun createFeedbackSystemInfoData(isToolWindowNamesEnabled: Boolean,
                                         isNewUINowEnabled: Boolean,
                                         layout: Map<ToolWindowAnchor, Int>): TwNamesFeedbackSystemData {
  return TwNamesFeedbackSystemData(isNewUINowEnabled,
                                   isToolWindowNamesEnabled,
                                   layout[ToolWindowAnchor.TOP]!!,
                                   layout[ToolWindowAnchor.LEFT]!!,
                                   layout[ToolWindowAnchor.BOTTOM]!!,
                                   layout[ToolWindowAnchor.RIGHT]!!,
                                   CommonFeedbackSystemData.getCurrentData())
}