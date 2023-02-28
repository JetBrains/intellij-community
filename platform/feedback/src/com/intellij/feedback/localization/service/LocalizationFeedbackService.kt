// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.localization.service

import com.intellij.DynamicBundle
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.PlatformUtils
import com.intellij.util.application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.*

@Service(Service.Level.APP)
@State(name = "LocalizationFeedbackState", storages = [Storage("LocalizationFeedbackState.xml")])
class LocalizationFeedbackService(private val coroutineScope: CoroutineScope) : PersistentStateComponent<LocalizationFeedbackService.State> {
  companion object {
    fun getInstance() = service<LocalizationFeedbackService>()

    fun isTesting() = System.getProperty("ide.feedback.localization.test")?.toBoolean() == true

    private const val DAYS_TO_WAIT = 3
    private val REFRESH_TIME_MS = if (isTesting()) Duration.ofSeconds(10).toMillis() else Duration.ofMinutes(30).toMillis()
  }

  private var myState = State()

  val logger  = logger<LocalizationFeedbackService>()


  class State : BaseState() {
    var daysUsedSincePluginInstallation by property(-1)
    var lastUpdateTime by property(-1L)
    var balloonWasInteractedWith by property(false)
  }

  fun getLanguagePack() =
    DynamicBundle.findLanguageBundle()?.pluginDescriptor?.let { it.pluginId.idString to it.version } ?: ("none" to "none")

  fun isEnabled() = (PlatformUtils.isRider() && application.isEAP) || isTesting()

  fun hasLanguagePack() = getLanguagePack().first != "none"

  fun tryRecordInstallation(): Boolean {
    if (!hasLanguagePack()) return false
    val state = myState
    if (state.daysUsedSincePluginInstallation != -1) return false
    state.daysUsedSincePluginInstallation = 1
    state.lastUpdateTime = System.currentTimeMillis()

    return true
  }

  fun setInteraction() { state.balloonWasInteractedWith = true }

  fun wasInteracted() = state.balloonWasInteractedWith

  private fun isTimeForNotification(): Boolean {
    val state = myState
    if (state.daysUsedSincePluginInstallation == -1) return false
    if (state.daysUsedSincePluginInstallation >= DAYS_TO_WAIT) return true

    val now = LocalDate.now(ZoneOffset.UTC)
    val lastUpdate = Instant.ofEpochMilli(state.lastUpdateTime).atOffset(ZoneOffset.UTC).toLocalDate()

    if (lastUpdate == now) {
      return state.daysUsedSincePluginInstallation >= DAYS_TO_WAIT
    }

    val lastIdeActionDate = Instant.ofEpochMilli(System.currentTimeMillis() - application.idleTime).atOffset(ZoneOffset.UTC).toLocalDate()

    if (lastIdeActionDate == now) {
      ++state.daysUsedSincePluginInstallation
      state.lastUpdateTime = System.currentTimeMillis()
    }

    return state.daysUsedSincePluginInstallation >= DAYS_TO_WAIT
  }

  fun runWatcher() {
    val state = myState
    if (state.daysUsedSincePluginInstallation == -1) return
    if (wasInteracted()) return

    coroutineScope.launch {
      while (!isTimeForNotification() || ProjectManager.getInstance().openProjects.isEmpty()) {
        delay(REFRESH_TIME_MS)
        logger.info("Not time for notification, current: ${state.daysUsedSincePluginInstallation}/$DAYS_TO_WAIT, opened projects: ${ProjectManager.getInstance().openProjects.isEmpty()}")
      }

      logger.info("Starting notification flow")

      delay(10000)

      LocalizationFeedbackNotificationService.getInstance().showNotification()
    }
  }

  override fun getState() = myState

  override fun loadState(state: State) {
    myState = state
  }
}