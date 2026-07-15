// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.wsl

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.execution.wsl.WslIjentAvailabilityService
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialogWithEmail
import com.intellij.platform.feedback.dialog.CommonFeedbackSystemData
import com.intellij.platform.feedback.dialog.showFeedbackSystemInfoDialog
import com.intellij.platform.feedback.dialog.uiBlocks.CheckBoxGroupBlock
import com.intellij.platform.feedback.dialog.uiBlocks.CheckBoxItemData
import com.intellij.platform.feedback.dialog.uiBlocks.ComboBoxBlock
import com.intellij.platform.feedback.dialog.uiBlocks.DescriptionBlock
import com.intellij.platform.feedback.dialog.uiBlocks.FeedbackBlock
import com.intellij.platform.feedback.dialog.uiBlocks.RatingBlock
import com.intellij.platform.feedback.dialog.uiBlocks.RatingGroupBlock
import com.intellij.platform.feedback.dialog.uiBlocks.RatingItem
import com.intellij.platform.feedback.dialog.uiBlocks.TextAreaBlock
import com.intellij.platform.feedback.dialog.uiBlocks.TopLabelBlock
import com.intellij.platform.feedback.impl.notification.ThanksForFeedbackNotification
import com.intellij.ui.ScreenUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Dimension
import java.awt.Rectangle
import java.nio.file.Path
import javax.swing.JComponent

internal class WslSatisfactionFeedbackDialog(
  private val project: Project,
  forTest: Boolean,
) : BlockBasedFeedbackDialogWithEmail<WslSatisfactionSystemData>(project, forTest) {

  /** Increase the additional number when feedback format is changed */
  override val myFeedbackJsonVersion: Int = super.myFeedbackJsonVersion + 1

  override val myFeedbackReportId: String = "wsl_satisfaction"
  override val zendeskTicketTitle: String = "WSL Support in-IDE Feedback"
  override val zendeskFeedbackType: String = "WSL Support Feedback"
  override fun shouldAutoCloseZendeskTicket(): Boolean = false

  private val ideName: String = ApplicationNamesInfo.getInstance().fullProductName

  override suspend fun computeSystemInfoData(): WslSatisfactionSystemData {
    val distribution = withContext(Dispatchers.IO) { findWslDistribution(project.basePath) }
    val isEelApiUsed = WslIjentAvailabilityService.getInstance().runWslCommandsViaIjent()
    return WslSatisfactionSystemData(
      ideName = ideName,
      wslDistribution = distribution?.presentableName ?: "UNKNOWN",
      wslVersion = distribution?.version ?: -1,
      isEelApiUsed = isEelApiUsed,
      commonData = CommonFeedbackSystemData.getCurrentData(),
    )
  }

  private fun findWslDistribution(projectPath: String?): WSLDistribution? {
    if (projectPath == null) return null
    return WslDistributionManager.getInstance().installedDistributions.firstOrNull {
      runCatching { Path.of(projectPath).startsWith(Path.of(it.getUNCRootPath().toUri())) }.getOrDefault(false)
    }
  }

  override fun showFeedbackSystemInfoDialog(systemInfoData: WslSatisfactionSystemData) {
    showFeedbackSystemInfoDialog(myProject, systemInfoData.commonData) {
      row(WslSatisfactionFeedbackBundle.message("wsl.satisfaction.system.info.ide.name")) {
        label(systemInfoData.ideName)
      }
      row(WslSatisfactionFeedbackBundle.message("wsl.satisfaction.system.info.distribution")) {
        label(systemInfoData.wslDistribution)
      }
      row(WslSatisfactionFeedbackBundle.message("wsl.satisfaction.system.info.wsl.version")) {
        label(systemInfoData.wslVersion.toString())
      }
      row(WslSatisfactionFeedbackBundle.message("wsl.satisfaction.system.info.eel.api.used")) {
        label(systemInfoData.isEelApiUsed.toString())
      }
    }
  }

  override val myTitle: String = WslSatisfactionFeedbackBundle.message("wsl.satisfaction.dialog.top.title")

  override val myBlocks: List<FeedbackBlock> = listOf(
    TopLabelBlock(WslSatisfactionFeedbackBundle.message("wsl.satisfaction.dialog.title")),
    DescriptionBlock(WslSatisfactionFeedbackBundle.message("wsl.satisfaction.dialog.description", ideName)),

    RatingBlock(
      WslSatisfactionFeedbackBundle.message("wsl.satisfaction.dialog.rating.label"),
      "satisfaction",
    ).setHint(WslSatisfactionFeedbackBundle.message("wsl.satisfaction.dialog.rating.hint")),

    RatingGroupBlock(
      WslSatisfactionFeedbackBundle.message("wsl.satisfaction.dialog.areas.label"),
      listOf(
        RatingItem(WslSatisfactionFeedbackBundle.message("wsl.satisfaction.dialog.area.stability"), "stability"),
        RatingItem(WslSatisfactionFeedbackBundle.message("wsl.satisfaction.dialog.area.responsiveness"), "responsiveness"),
        RatingItem(WslSatisfactionFeedbackBundle.message("wsl.satisfaction.dialog.area.quality"), "overall_quality"),
      ),
    ).setHint(WslSatisfactionFeedbackBundle.message("wsl.satisfaction.dialog.areas.hint")),

    ComboBoxBlock(
      WslSatisfactionFeedbackBundle.message("wsl.satisfaction.dialog.freeze.comparison.label"),
      listOf(
        WslSatisfactionFeedbackBundle.message("wsl.satisfaction.dialog.freeze.comparison.less"),
        WslSatisfactionFeedbackBundle.message("wsl.satisfaction.dialog.freeze.comparison.same"),
        WslSatisfactionFeedbackBundle.message("wsl.satisfaction.dialog.freeze.comparison.more"),
        WslSatisfactionFeedbackBundle.message("wsl.satisfaction.dialog.freeze.comparison.unsure"),
      ),
      "freeze_comparison",
    ).useWrappingLabel(),

    ComboBoxBlock(
      WslSatisfactionFeedbackBundle.message("wsl.satisfaction.dialog.freeze.frequency.label"),
      listOf(
        WslSatisfactionFeedbackBundle.message("wsl.satisfaction.dialog.freeze.frequency.several.day"),
        WslSatisfactionFeedbackBundle.message("wsl.satisfaction.dialog.freeze.frequency.once.day"),
        WslSatisfactionFeedbackBundle.message("wsl.satisfaction.dialog.freeze.frequency.several.week"),
        WslSatisfactionFeedbackBundle.message("wsl.satisfaction.dialog.freeze.frequency.monthly"),
        WslSatisfactionFeedbackBundle.message("wsl.satisfaction.dialog.freeze.frequency.rarely"),
      ),
      "freeze_frequency",
    ).useWrappingLabel(),

    CheckBoxGroupBlock(
      WslSatisfactionFeedbackBundle.message("wsl.satisfaction.dialog.freeze.situations.label"),
      listOf(
        CheckBoxItemData(WslSatisfactionFeedbackBundle.message("wsl.satisfaction.dialog.freeze.situations.none"), "no_freezes"),
        CheckBoxItemData(WslSatisfactionFeedbackBundle.message("wsl.satisfaction.dialog.freeze.situations.opening"), "opening_project"),
        CheckBoxItemData(WslSatisfactionFeedbackBundle.message("wsl.satisfaction.dialog.freeze.situations.indexing"), "indexing"),
        CheckBoxItemData(WslSatisfactionFeedbackBundle.message("wsl.satisfaction.dialog.freeze.situations.running"), "running_debugging"),
        CheckBoxItemData(WslSatisfactionFeedbackBundle.message("wsl.satisfaction.dialog.freeze.situations.terminal"), "terminal"),
        CheckBoxItemData(WslSatisfactionFeedbackBundle.message("wsl.satisfaction.dialog.freeze.situations.vcs"), "vcs"),
        CheckBoxItemData(WslSatisfactionFeedbackBundle.message("wsl.satisfaction.dialog.freeze.situations.docker"), "docker"),
        CheckBoxItemData(WslSatisfactionFeedbackBundle.message("wsl.satisfaction.dialog.freeze.situations.editing"), "editing"),
        CheckBoxItemData(WslSatisfactionFeedbackBundle.message("wsl.satisfaction.dialog.freeze.situations.branches"), "switching_branches"),
        CheckBoxItemData(WslSatisfactionFeedbackBundle.message("wsl.satisfaction.dialog.freeze.situations.search"), "search_navigation"),
      ),
      "freeze_situations",
    ).setColumnCount(2).addOtherTextField(),

    TextAreaBlock(
      WslSatisfactionFeedbackBundle.message("wsl.satisfaction.dialog.additional.label", ideName),
      "additional_feedback",
    ),
  )

  init {
    init()
  }

  override fun createCenterPanel(): JComponent {
    val centerPanel = super.createCenterPanel()
    // The survey is long; cap the default height at 80% of the target screen (per the platform window-size
    // guidelines) and let the content scroll, instead of growing the dialog to the full content height.
    val maxHeight = (targetScreenBounds().height * 0.8).toInt()
    val preferred = centerPanel.preferredSize
    if (preferred.height > maxHeight) {
      centerPanel.preferredSize = Dimension(preferred.width, maxHeight)
    }
    return centerPanel
  }

  /** Bounds of the screen the dialog will appear on (it is centered over the parent project frame). */
  private fun targetScreenBounds(): Rectangle {
    val frame = myProject?.let { WindowManager.getInstance().getFrame(it) }
    return if (frame != null) ScreenUtil.getScreenRectangle(frame) else ScreenUtil.getMainScreenBounds()
  }

  override fun showThanksNotification() {
    ThanksForFeedbackNotification(
      description = WslSatisfactionFeedbackBundle.message("wsl.satisfaction.thanks.feedback.content")
    ).notify(myProject)
  }
}
