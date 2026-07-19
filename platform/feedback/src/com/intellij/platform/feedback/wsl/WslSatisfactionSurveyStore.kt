// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.wsl

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.application.InitialConfigImportState
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import java.time.Duration
import java.time.Instant

private val WSL_PATH_PREFIXES = listOf("//wsl$/", "//wsl.localhost/")

/**
 * Whether [this] project is opened from a WSL file system.
 *
 * Uses the same lightweight base-path prefix check as
 * [com.intellij.featureStatistics.fusCollectors.WslUsagesCollector], so it can be called
 * on any thread without touching the (potentially slow) distribution manager.
 */
@OptIn(LowLevelLocalMachineAccess::class)
internal fun Project.isWslProject(): Boolean {
  if (OS.CURRENT != OS.Windows) return false
  val projectPath = guessProjectDir()?.path ?: return false
  return WSL_PATH_PREFIXES.any { projectPath.startsWith(it) }
}

internal class WslSatisfactionSurveyState : BaseState() {
  // Unix time seconds of the first detected WSL project open.
  var firstWslUseTime: Long by property(0L)

  // Number of distinct days on which a WSL project was open.
  var daysWithWslProject: Int by property(0)

  // Unix time seconds; [daysWithWslProject] is incremented at most once after this moment.
  var nextCountedDay: Long by property(0L)
  var userSawSurvey: Boolean by property(false)

  // Whether the IDE was first launched as a new user. Captured once on the first run, because
  // InitialConfigImportState.isNewUser() is only meaningful during the very first session and is always
  // false afterwards (when the survey can actually be shown).
  var newUserStatusRecorded: Boolean by property(false)
  var startedAsNewUser: Boolean by property(false)
}

@State(name = "WslSatisfactionSurveyStore", storages = [Storage(value = "wsl-survey.xml", roamingType = RoamingType.DISABLED)])
internal class WslSatisfactionSurveyStore : PersistentStateComponent<WslSatisfactionSurveyState> {

  internal var currentState: WslSatisfactionSurveyState = WslSatisfactionSurveyState()

  override fun getState(): WslSatisfactionSurveyState = currentState

  override fun loadState(state: WslSatisfactionSurveyState) {
    currentState = state
  }

  internal fun recordSurveyShown() {
    currentState.userSawSurvey = true
  }

  /**
   * Captures, once, whether the IDE was first launched as a new user. Must be called early (e.g. from a
   * startup activity) so the value reflects the first session, where [InitialConfigImportState.isNewUser]
   * is meaningful.
   */
  internal fun recordNewUserStatusIfNeeded() {
    if (!currentState.newUserStatusRecorded) {
      currentState.newUserStatusRecorded = true
      currentState.startedAsNewUser = InitialConfigImportState.isNewUser()
    }
  }

  internal fun startedAsNewUser(): Boolean = currentState.startedAsNewUser

  /**
   * Records that a WSL project was open now. Increments [WslSatisfactionSurveyState.daysWithWslProject]
   * at most once per calendar day and stamps the first-usage time.
   */
  internal fun recordWslProjectOpened() {
    val now = Instant.now()
    if (now >= Instant.ofEpochSecond(currentState.nextCountedDay)) {
      currentState.daysWithWslProject++
      currentState.nextCountedDay = (now + Duration.ofDays(1)).epochSecond
    }
    if (currentState.firstWslUseTime == 0L) {
      currentState.firstWslUseTime = now.epochSecond
    }
  }

  internal fun shouldShowDialog(): Boolean {
    val state = currentState
    if (state.userSawSurvey) return false

    val daysWithWslProject = Registry.intValue(REGISTRY_DAYS_WITH_WSL, -1).takeIf { it >= 0 } ?: state.daysWithWslProject
    if (daysWithWslProject < MINIMUM_DAYS_WITH_WSL_PROJECT) return false

    val firstWslUseTime = firstWslUseTime() ?: return false
    return Duration.between(firstWslUseTime, Instant.now()) >= MINIMUM_DURATION_SINCE_FIRST_USE
  }

  private fun firstWslUseTime(): Instant? {
    val override = Registry.stringValue(REGISTRY_FIRST_USE_DATE)
    if (override.isNotBlank()) {
      return runCatching { java.time.LocalDate.parse(override).atStartOfDay(java.time.ZoneOffset.UTC).toInstant() }.getOrNull()
    }
    if (currentState.firstWslUseTime == 0L) return null
    return Instant.ofEpochSecond(currentState.firstWslUseTime)
  }

  companion object {
    // "Project opened in WSL x3" + "after 3-5 days working with WSL" (IJPL-246739 brief).
    internal const val MINIMUM_DAYS_WITH_WSL_PROJECT: Int = 3
    internal val MINIMUM_DURATION_SINCE_FIRST_USE: Duration = Duration.ofDays(3)

    internal const val REGISTRY_FIRST_USE_DATE: String = "wsl.survey.first.use.date"
    internal const val REGISTRY_DAYS_WITH_WSL: String = "wsl.survey.days.with.wsl"

    fun getInstance(): WslSatisfactionSurveyStore = service()
  }
}
