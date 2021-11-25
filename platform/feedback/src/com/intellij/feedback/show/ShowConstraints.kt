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

// 20 minutes
internal val MIN_INACTIVE_TIME = 20

internal fun checkLastActive(lastActiveDateTime: LocalDateTime): Boolean {
  return Duration.between(LocalDateTime.now(), lastActiveDateTime).toMinutes() >= MIN_INACTIVE_TIME
}

internal fun isIntellijIdeaEAP(): Boolean {
  return ApplicationInfoEx.getInstanceEx().isEAP
}

internal fun showInactiveTimeNotificationIfPossible(project: Project) {
  if (checkLastActive(LastActive.lastActive) && Registry.`is`("platform.feedback", false)) {
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
        lastNewProjectInfoEntry.projectBuilderId,
        lastNewProjectInfoEntry.creationDateTime,
        true,
        true))

    return lastNewProjectInfoEntry.projectBuilderId
  }
  else {
    return null
  }
}
