// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.localization.service

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration

@Service(Service.Level.APP)
@State(name = "LocalizationFeedbackState", storages = [Storage("LocalizationFeedbackState.xml")])
class LocalizationFeedbackService(private val coroutineScope: CoroutineScope) : PersistentStateComponent<LocalizationFeedbackService.State> {
  companion object {
    fun getInstance() = service<LocalizationFeedbackService>()

    private val TIME_TO_WAIT_FOR_NOTIFICATION_MS = System.getProperty("ide.feedback.localization.wait.ms")?.toLong()
                                                   ?: Duration.ofDays(3).toMillis()
    private val REFRESH_TIME_MS = System.getProperty("ide.feedback.localization.refresh.ms")?.toLong()
                                  ?: Duration.ofHours(1).toMillis()
  }

  private var myState: State? = null

  enum class Languages(val pluginId: String) {
    Japanese("com.intellij.ja"),
    Chinese("com.intellij.zh"),
    Korean("com.intellij.ko")
  }

  class State : BaseState() {
    var lastPluginInstallationDate by property(0L)
    var balloonWasInteractedWith by property(false)
  }

  fun tryRecordInstallation() {
    val state = myState ?: return
    if (state.lastPluginInstallationDate != 0L) return
    state.lastPluginInstallationDate = System.currentTimeMillis()
  }

  fun setInteraction() {
    state?.balloonWasInteractedWith = true
  }

  fun runWatcher() {
    val state = myState ?: return
    if (state.lastPluginInstallationDate == 0L) return
    if (state.balloonWasInteractedWith) return
    coroutineScope.launch {
      while ((System.currentTimeMillis() - state.lastPluginInstallationDate) >= TIME_TO_WAIT_FOR_NOTIFICATION_MS) {
        delay(REFRESH_TIME_MS)
      }
      ProjectManager.getInstance().apply {
        val activeProject = ProjectUtil.getActiveProject()
        if (activeProject != null) {
          showNotification(activeProject)
          return@apply
        }
        if (openProjects.isNotEmpty()) {
          openProjects.forEach {
            showNotification(it)
          }
        }
        else {
          // somehow schedule for project opening
        }
      }
    }
  }

  fun showNotification(project: Project) {
    LocalizationFeedbackNotificationService.getInstance().showNotification(project)
  }

  override fun getState() = myState

  override fun loadState(state: State) {
    myState = state
  }
}