// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.feedback

import com.intellij.feedback.FeedbackTypeResolver.isFeedbackNotificationDisabled
import com.intellij.feedback.bundle.FeedbackBundle
import com.intellij.feedback.dialog.ProjectCreationFeedbackDialog
import com.intellij.feedback.notification.RequestFeedbackNotification
import com.intellij.feedback.state.projectCreation.ProjectCreationInfoService
import com.intellij.feedback.state.projectCreation.ProjectCreationInfoState
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper

enum class FeedbackTypes {
  PROJECT_CREATION_FEEDBACK {
    private val unknownProjectTypeName = "UNKNOWN"
    private val testProjectTypeName = "TEST"
    private val maxNumberNotificationShowed = 3
    override val suitableIdeVersion: String = "2022.1"

    override fun isSuitable(): Boolean {
      val projectCreationInfoState = ProjectCreationInfoService.getInstance().state

      return isIntellijIdeaEAP() &&
             checkIdeVersionIsSuitable() &&
             checkProjectCreationFeedbackNotSent(projectCreationInfoState) &&
             checkProjectCreated(projectCreationInfoState) &&
             checkNotificationNumberNotExceeded(projectCreationInfoState)
    }

    private fun checkProjectCreationFeedbackNotSent(state: ProjectCreationInfoState): Boolean {
      return !state.feedbackSent
    }

    private fun checkProjectCreated(state: ProjectCreationInfoState): Boolean {
      return state.lastCreatedProjectBuilderId != null
    }

    private fun checkNotificationNumberNotExceeded(state: ProjectCreationInfoState): Boolean {
      return state.numberNotificationShowed < maxNumberNotificationShowed
    }

    override fun createNotification(forTest: Boolean): Notification {
      return RequestFeedbackNotification()
    }

    override fun createFeedbackDialog(project: Project?, forTest: Boolean): DialogWrapper {
      return ProjectCreationFeedbackDialog(project, getLastCreatedProjectTypeName(forTest), forTest)
    }

    private fun getLastCreatedProjectTypeName(forTest: Boolean): String {
      if (forTest) {
        return testProjectTypeName
      }

      val projectCreationInfoState = ProjectCreationInfoService.getInstance().state
      return projectCreationInfoState.lastCreatedProjectBuilderId ?: unknownProjectTypeName
    }

    override fun updateStateAfterNotificationShowed() {
      val projectCreationInfoState = ProjectCreationInfoService.getInstance().state
      projectCreationInfoState.numberNotificationShowed += 1
    }
  };

  protected abstract val suitableIdeVersion: String

  abstract fun isSuitable(): Boolean

  protected fun isIntellijIdeaEAP(): Boolean {
    return ApplicationInfoEx.getInstanceEx().isEAP
  }

  protected fun checkIdeVersionIsSuitable(): Boolean {
    return suitableIdeVersion == ApplicationInfoEx.getInstanceEx().shortVersion
  }

  protected abstract fun createNotification(forTest: Boolean): Notification

  protected abstract fun createFeedbackDialog(project: Project?, forTest: Boolean): DialogWrapper

  protected abstract fun updateStateAfterNotificationShowed()
  
  fun showNotification(project: Project?, forTest: Boolean = false) {
    val notification = createNotification(forTest)
    notification.addAction(
      NotificationAction.createSimpleExpiring(FeedbackBundle.message("notification.request.feedback.action.respond.text")) {
        val dialog = createFeedbackDialog(project, forTest)
        dialog.show()
      }
    )
    notification.addAction(
      NotificationAction.createSimpleExpiring(FeedbackBundle.message("notification.request.feedback.action.dont.show.text")) {
        if (!forTest) {
          isFeedbackNotificationDisabled = true
        }
      }
    )
    notification.notify(project)
    if (!forTest) {
      updateStateAfterNotificationShowed()
    }
  }
}