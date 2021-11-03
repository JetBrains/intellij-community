// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.feedback.show

import com.intellij.feedback.FeedbackTypeResolver
import com.intellij.feedback.state.active.LastActive
import com.intellij.feedback.state.createdProject.NewProjectInfoEntry
import com.intellij.feedback.state.createdProject.NewProjectStatisticService
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import java.time.Duration
import java.time.LocalDateTime

/**
 * Contains constants and functions that implement restrictions on displaying notifications.
 */

// 45 days
private const val DEFAULT_MIN_DAYS_SINCE_SEND_FEEDBACK = 45

// 60 days
private const val DEFAULT_MIN_DAYS_SINCE_CLOSE_FEEDBACK_DIALOG = 60

// 15 days
private const val DEFAULT_MIN_DAYS_SINCE_SHOW_FEEDBACK_NOTIFICATION = 15

// 10 minutes
private const val DEFAULT_MIN_INACTIVE_TIME = 10


/*
internal val MIN_DAYS_SINCE_SEND_FEEDBACK = getMinDaysSinceSendFeedback() ?: DEFAULT_MIN_DAYS_SINCE_SEND_FEEDBACK

internal val MIN_DAYS_SINCE_CLOSE_FEEDBACK_DIALOG = getMinDaysSinceCloseFeedbackDialog()
        ?: DEFAULT_MIN_DAYS_SINCE_CLOSE_FEEDBACK_DIALOG

internal val MIN_DAYS_SINCE_SHOW_FEEDBACK_NOTIFICATION = getMinDaysSinceShowNotification()
        ?: DEFAULT_MIN_DAYS_SINCE_SHOW_FEEDBACK_NOTIFICATION

internal val MIN_DURATION_COMPILE_TASK = getMinDurationCompileTask() ?: DEFAULT_MIN_DURATION_COMPILE_TASK

internal val MIN_DURATION_GRADLE_TASK = getMinDurationGradleTask() ?: DEFAULT_MIN_DURATION_GRADLE_TASK
*/

//TODO: Is there configured from external source like json file on github or something else
internal val MIN_INACTIVE_TIME = /*getMinInactiveTime() ?: */DEFAULT_MIN_INACTIVE_TIME


/*
internal fun checkFeedbackDatesForNotifications(): Boolean {
    val feedbackDatesState: FeedbackDatesState = service<FeedbackDatesService>().state ?: return false
    val dayFromLastSendFeedback = Duration.between(
            feedbackDatesState.dateSendFeedback.atStartOfDay(),
            LocalDate.now().atStartOfDay()).toDays()
    val dayFromLastCloseFeedbackDialog = Duration.between(
            feedbackDatesState.dateCloseFeedbackDialog.atStartOfDay(),
            LocalDate.now().atStartOfDay()).toDays()
    val dayFromLastShowFeedbackNotification = Duration.between(
            feedbackDatesState.dateShowFeedbackNotification.atStartOfDay(),
            LocalDate.now().atStartOfDay()).toDays()
    return dayFromLastSendFeedback >= MIN_DAYS_SINCE_SEND_FEEDBACK
            && dayFromLastCloseFeedbackDialog >= MIN_DAYS_SINCE_CLOSE_FEEDBACK_DIALOG
            && dayFromLastShowFeedbackNotification >= MIN_DAYS_SINCE_SHOW_FEEDBACK_NOTIFICATION
}

internal fun checkFeedbackDatesForWidget(): Boolean {
    val feedbackDatesState: FeedbackDatesState = service<FeedbackDatesService>().state ?: return false
    val dayFromLastSendFeedback = Duration.between(
            feedbackDatesState.dateSendFeedback.atStartOfDay(),
            LocalDate.now().atStartOfDay()).toDays()
    val dayFromLastCloseFeedbackDialog = Duration.between(
            feedbackDatesState.dateCloseFeedbackDialog.atStartOfDay(),
            LocalDate.now().atStartOfDay()).toDays()
    return dayFromLastSendFeedback >= MIN_DAYS_SINCE_SEND_FEEDBACK
            && dayFromLastCloseFeedbackDialog >= MIN_DAYS_SINCE_CLOSE_FEEDBACK_DIALOG
}
*/
internal fun checkLastActive(lastActiveDateTime: LocalDateTime): Boolean {
  return Duration.between(LocalDateTime.now(), lastActiveDateTime).toMinutes() >= MIN_INACTIVE_TIME
}

internal fun isIntellijIdeaEAP(): Boolean {
  return ApplicationInfoEx.getInstanceEx().isEAP
}

internal fun showInactiveTimeNotificationIfPossible(project: Project) {
  if (checkLastActive(LastActive.lastActive) && Registry.`is`("platform.feedback", false)
  /*&& checkFeedbackDatesForNotifications()*/) {
    val lastNotProcessedCreatedProjectTypeName = findLastNotProcessedCreatedProjectTypeName()
    if (lastNotProcessedCreatedProjectTypeName != null) {
      FeedbackTypeResolver.showProjectCreationFeedbackNotification(project, lastNotProcessedCreatedProjectTypeName)
    }
  }
}

internal fun findLastNotProcessedCreatedProjectTypeName(): String? {
  val newProjectStatisticService = service<NewProjectStatisticService>()
  val newProjectInfoState = newProjectStatisticService.state
  newProjectInfoState.createdProjectInfo.sortBy { it.creationDateTime }
  val lastNewProjectInfoEntry = newProjectInfoState.createdProjectInfo.lastOrNull {
    !it.isProcessed
  }

  if (lastNewProjectInfoEntry != null) {
    newProjectInfoState.createdProjectInfo.remove(lastNewProjectInfoEntry)
    newProjectInfoState.createdProjectInfo.add(
      NewProjectInfoEntry(
        lastNewProjectInfoEntry.projectTypeName,
        lastNewProjectInfoEntry.creationDateTime,
        true,
        true))

    return lastNewProjectInfoEntry.projectTypeName
  }
  else {
    return null
  }
}
