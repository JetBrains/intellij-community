// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.pluginPage

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialogWithEmail
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
import java.util.*

internal enum class CaseType {
  DISABLE, UNINSTALL
}

internal abstract class PluginPageFeedbackDialog(private val pluginId: String,
                                                 pluginName: String,
                                                 caseType: CaseType,
                                                 project: Project?,
                                                 forTest: Boolean) :
  BlockBasedFeedbackDialogWithEmail<PluginPageFeedbackSystemData>(project, forTest) {

  private val pluginNameCapitalized = pluginName.replaceFirstChar {
    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
  }
  private val messageIdPrefixForCase = caseType.name.lowercase()

  override val myFeedbackJsonVersion: Int = super.myFeedbackJsonVersion + 1
  override val zendeskTicketTitle: String = PluginPageFeedbackBundle.message("dialog.zendesk.ticket.title", pluginNameCapitalized)
  override val myTitle: String = PluginPageFeedbackBundle.message("dialog.top.title")

  override suspend fun computeSystemInfoData(): PluginPageFeedbackSystemData {
    val commonFeedbackSystemData = CommonFeedbackSystemData.getCurrentData()
    return PluginPageFeedbackSystemData(pluginId, pluginNameCapitalized, commonFeedbackSystemData)
  }

  override fun showFeedbackSystemInfoDialog(systemInfoData: PluginPageFeedbackSystemData) {
    showPluginPageFeedbackSystemInfoDialog(myProject, systemInfoData)
  }

  private val reasonsItems: List<CheckBoxItemData> = listOf(
    CheckBoxItemData(PluginPageFeedbackBundle.message("dialog.checkbox.group.option.1.label"),
                     "installed_by_accident"),
    CheckBoxItemData(PluginPageFeedbackBundle.message("dialog.checkbox.group.option.2.label"),
                     "missing_features"),
    CheckBoxItemData(PluginPageFeedbackBundle.message("dialog.checkbox.group.option.3.label"),
                     "serious_bugs"),
  )

  override val myBlocks: List<FeedbackBlock> = listOf(
    TopLabelBlock(PluginPageFeedbackBundle.message("dialog.title", pluginNameCapitalized))
      .setBottomGap(BottomGap.MEDIUM),
    CheckBoxGroupBlock(PluginPageFeedbackBundle.message("$messageIdPrefixForCase.dialog.checkbox.group.label"),
                       reasonsItems, "reasons").addOtherTextField().requireAnswer(),
    TextAreaBlock(PluginPageFeedbackBundle.message("dialog.textarea.label"), "what_to_improve")
  )

  override fun showThanksNotification() {
    ThanksForFeedbackNotification(description = PluginPageFeedbackBundle.message(
      "notification.thanks.feedback.content")).notify(myProject)
  }

  override fun shouldAutoCloseZendeskTicket(): Boolean {
    val pluginDescriptor = PluginManagerCore.getPlugin(PluginId.getId(pluginId))
    val pluginVendor = pluginDescriptor?.vendor ?: return true

    return !StringUtil.equalsIgnoreCase(pluginVendor, "JetBrains")
  }
}

@Serializable
internal data class PluginPageFeedbackSystemData(
  @NlsSafe val pluginId: String,
  @NlsSafe val pluginName: String,
  val commonSystemInfo: CommonFeedbackSystemData
) : SystemDataJsonSerializable {
  override fun toString(): String {
    return buildString {
      appendLine(PluginPageFeedbackBundle.message("dialog.system.info.plugin.id"))
      appendLine(pluginId)
      appendLine(PluginPageFeedbackBundle.message("dialog.system.info.plugin.name"))
      appendLine(pluginName)
      appendLine()
      commonSystemInfo.toString()
    }
  }

  override fun serializeToJson(json: Json): JsonElement {
    return json.encodeToJsonElement(this)
  }
}

private fun showPluginPageFeedbackSystemInfoDialog(project: Project?, systemInfoData: PluginPageFeedbackSystemData) =
  showFeedbackSystemInfoDialog(project, systemInfoData.commonSystemInfo) {
    row(PluginPageFeedbackBundle.message("dialog.system.info.plugin.id")) {
      label(systemInfoData.pluginId)
    }
    row(PluginPageFeedbackBundle.message("dialog.system.info.plugin.name")) {
      label(systemInfoData.pluginName)
    }
  }