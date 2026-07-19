// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.wsl

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.Project
import com.intellij.platform.feedback.InIdeFeedbackSurveyConfig
import com.intellij.platform.feedback.dialog.BlockBasedFeedbackDialog
import com.intellij.platform.feedback.dialog.SystemDataJsonSerializable
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.feedback.impl.notification.RequestFeedbackNotification
import com.intellij.util.PlatformUtils
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import kotlinx.datetime.LocalDate

internal class WslSatisfactionSurveyConfig : InIdeFeedbackSurveyConfig {

  override val surveyId: String = "wsl_satisfaction"

  // Survey is targeted at 2026.2 + 2026.2.x; collect for ~two months after release. Adjust to the actual release date.
  override val lastDayOfFeedbackCollection: LocalDate = LocalDate(2026, 10, 1)

  override val requireIdeEAP: Boolean = false

  @OptIn(LowLevelLocalMachineAccess::class)
  override fun checkIdeIsSuitable(): Boolean {
    // WSL exists only on Windows; the brief limits the survey to these IDEs.
    return OS.CURRENT == OS.Windows &&
           (PlatformUtils.isIdeaUltimate() ||
            PlatformUtils.isIdeaCommunity() ||
            PlatformUtils.isPyCharm() ||
            PlatformUtils.isWebStorm() ||
            PlatformUtils.isPhpStorm() ||
            PlatformUtils.isGoIde())
  }

  override fun checkExtraConditionSatisfied(project: Project): Boolean {
    if (!project.isWslProject()) return false
    val store = WslSatisfactionSurveyStore.getInstance()
    if (!store.shouldShowDialog()) return false
    // Testing shortcut: ignore the version and "existing users only" audience gates.
    if (Registry.`is`(REGISTRY_IGNORE_AUDIENCE_CONDITIONS, false)) return true
    // Show only for the target version and existing (non-new) users.
    // The new-user status is captured on the first run (see WslSatisfactionSurveyStore.recordNewUserStatusIfNeeded),
    // because InitialConfigImportState.isNewUser() is only meaningful during the very first session.
    return isTargetVersion() && !store.startedAsNewUser()
  }

  private fun isTargetVersion(): Boolean {
    return ApplicationInfo.getInstance().shortVersion == TARGET_VERSION
  }

  override fun createFeedbackDialog(project: Project, forTest: Boolean): BlockBasedFeedbackDialog<out SystemDataJsonSerializable> {
    return WslSatisfactionFeedbackDialog(project, forTest)
  }

  override fun updateStateAfterDialogClosedOk(project: Project) {
    WslSatisfactionSurveyStore.getInstance().recordSurveyShown()
  }

  override fun createNotification(project: Project, forTest: Boolean): RequestFeedbackNotification {
    return RequestFeedbackNotification(
      "Feedback In IDE",
      WslSatisfactionFeedbackBundle.message("wsl.satisfaction.notification.request.title"),
      WslSatisfactionFeedbackBundle.message("wsl.satisfaction.notification.request.content"),
    )
  }

  override fun updateStateAfterNotificationShowed(project: Project) {
    // do nothing
  }

  companion object {
    private const val TARGET_VERSION: String = "2026.2"
    private const val REGISTRY_IGNORE_AUDIENCE_CONDITIONS: String = "wsl.survey.ignore.audience.conditions"
  }
}
