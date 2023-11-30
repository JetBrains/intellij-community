// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.localization.service

import com.intellij.DynamicBundle
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.PlatformUtils
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

    fun isTesting() = System.getProperty("ide.feedback.localization.test")?.toBoolean() == true

    private val TIME_TO_WAIT_FOR_NOTIFICATION_MS = if (isTesting()) Duration.ofMinutes(3).toMillis() else Duration.ofDays(3).toMillis()
    private val REFRESH_TIME_MS = if (isTesting()) Duration.ofMinutes(1).toMillis() else Duration.ofMinutes(30).toMillis()
  }

  private var myState = State()
  private var initialTimeSincePluginInstallation = -1L


  class State : BaseState() {
    var timeSincePluginInstallation by property(-1L)
    var balloonWasInteractedWith by property(false)
  }

  private fun getCurrentSessionTime() = (System.currentTimeMillis() - application.startTime - application.idleTime).coerceAtLeast(0)

  fun getLanguagePack() =
    DynamicBundle.findLanguageBundle()?.pluginDescriptor?.let { it.pluginId.idString to it.version } ?: ("none" to "none")

  fun isEnabled() = (PlatformUtils.isRider() && application.isEAP) || isTesting()

  private fun hasLanguagePack() = getLanguagePack().first != "none"

  fun tryRecordInstallation(): Boolean {
    if (!hasLanguagePack()) return false
    val state = myState
    if (state.timeSincePluginInstallation != -1L) return false
    state.timeSincePluginInstallation = 0L
    initialTimeSincePluginInstallation = 0L

    return true
  }

  fun setInteraction() { state.balloonWasInteractedWith = true }

  private fun wasInteracted() = state.balloonWasInteractedWith

  private fun isTimeForNotification(): Boolean {
    val state = myState

    val time = initialTimeSincePluginInstallation + getCurrentSessionTime()
    state.timeSincePluginInstallation = time

    return time >= TIME_TO_WAIT_FOR_NOTIFICATION_MS
  }

  fun runWatcher() {
    val state = myState
    if (state.timeSincePluginInstallation == -1L) return
    if (wasInteracted()) return

    coroutineScope.launch {
      while (!isTimeForNotification() || ProjectManager.getInstance().openProjects.isEmpty()) {
        delay(REFRESH_TIME_MS)
        thisLogger().debug("Not time for notification, current: ${state.timeSincePluginInstallation}")
      }

      thisLogger().info("Starting notification flow")

      delay(10000)

      LocalizationFeedbackNotificationService.getInstance().showNotification()
    }
  }

  override fun getState() = myState

  override fun loadState(state: State) {
    myState = state
    initialTimeSincePluginInstallation = state.timeSincePluginInstallation
  }
}