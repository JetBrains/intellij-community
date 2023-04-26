// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.aqua

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.PlatformUtils
import java.time.Duration
import java.time.Instant


const val AquaFirstSurveyIsEnabledKey = "intellij.feedback.aqua.survey1.isEnabled"
const val AquaSecondSurveyIsEnabledKey = "intellij.feedback.aqua.survey2.isEnabled"
const val AquaFirstUsageDateKey = "intellij.feedback.aqua.first.usage.epoch.seconds"
const val AquaSecondSurveyDelayInDays = 5

@Service(Service.Level.APP)
internal class AquaFeedbackSurveyTriggers {
  private var userTypedInEditor = false

  private val isAnyProjectOpenNowInAqua: Boolean
    get() = when {
      !PlatformUtils.isAqua() -> false
      else -> ProjectManager.getInstance().openProjects.count {
        !it.isDisposed
      } > 0
    }

  private val firstUsageTime: Instant?
    get() = PropertiesComponent.getInstance()
      .getValue(AquaFirstUsageDateKey)
      ?.toLong()
      ?.let {
        Instant.ofEpochSecond(it)
      }

  private val isFirstSurveyEnabled: Boolean
    get() = PropertiesComponent.getInstance().getBoolean(AquaFirstSurveyIsEnabledKey, true)

  private val isSecondSurveyEnabled: Boolean
    get() = PropertiesComponent.getInstance().getBoolean(AquaSecondSurveyIsEnabledKey, true)

  @Suppress("unused")
  internal fun suppressInitialSurvey() = PropertiesComponent.getInstance().setValue(AquaFirstSurveyIsEnabledKey, false)

  @Suppress("unused")
  internal fun suppressSecondarySurvey() = PropertiesComponent.getInstance().setValue(AquaSecondSurveyIsEnabledKey, false)

  internal fun logUserTyped() {
    userTypedInEditor = true
  }

  internal fun captureFirstUsageTime() {
    val propertiesComponent = PropertiesComponent.getInstance()

    if (propertiesComponent.isValueSet(AquaFirstUsageDateKey))
      return

    val now = Instant.now().epochSecond

    propertiesComponent.setValue(AquaFirstUsageDateKey, now.toString())
  }

  /**
   * Rules:
   * * Is Aqua IDE
   * * A project is open
   * * The survey is not passed or ignored
   * * Typed anything in the editor
   */
  @Suppress("unused")
  internal fun shouldTriggerSurvey1(): Boolean =
    isAnyProjectOpenNowInAqua
    && isFirstSurveyEnabled
    && userTypedInEditor

  /**
   * Rules:
   * * Is Aqua IDE
   * * A project is open
   * * The survey is not passed or ignored
   * * Typed anything in the editor
   * * [AquaSecondSurveyDelayInDays] days passed since the first usage
   */
  @Suppress("unused")
  internal fun shouldTriggerSurvey2(): Boolean =
    isAnyProjectOpenNowInAqua
    && isSecondSurveyEnabled
    && userTypedInEditor
    && firstUsageTime?.let { Duration.between(it, Instant.now()).toDays() >= AquaSecondSurveyDelayInDays } ?: false
}