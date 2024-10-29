// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.eap

import com.intellij.ide.ApplicationActivity
import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationAction
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.feedback.impl.notification.RequestFeedbackNotification
import com.intellij.util.application
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private const val timerStartedKey = "eap.feedback.scheduled"
private const val eapFeedbackRegistryKey = "eap.feedback.notification.enabled"

private class EAPApplicationInitializedListener : ApplicationActivity {
  init {
    if (!isEAPEnv()) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute() {
    coroutineScope {
      // postpone to avoid getting PropertiesComponent and other classes too early
      delay(1.minutes)
      schedule()
    }
  }

  private suspend fun schedule() {
    val application = ApplicationManager.getApplication()
    if (application.isDisposed) return
    if (!Registry.`is`(eapFeedbackRegistryKey)) return

    val propComponent = PropertiesComponent.getInstance()

    val parsedValue = getParseTimerStarted(propComponent)
    if (parsedValue == -1L) return

    val currentTimeMillis = System.currentTimeMillis()
    if (parsedValue != null) {
      if (System.currentTimeMillis() - parsedValue > 1.days.inWholeMilliseconds) {
        showNotificationAndDisableTimer(propComponent)
        return
      }
    }
    else {
      PropertiesComponent.getInstance().setValue(getTimerStartedKey(), currentTimeMillis.toString())
    }
    delay(5.hours)
    showNotificationAndDisableTimer(propComponent)
  }

  private fun showNotificationAndDisableTimer(propComponent: PropertiesComponent) {
    setEAPFeedbackShown(propComponent)
    showNotification()
    return
  }

  private fun showNotification() {
    val notification = RequestFeedbackNotification(
      "Feedback In IDE",
      EAPFeedbackBundle.message("notification.request.eap.feedback.title"),
      EAPFeedbackBundle.message("notification.request.eap.feedback.text")
    )

    @Suppress("DialogTitleCapitalization")
    notification.addAction(
      NotificationAction.createSimpleExpiring(
        EAPFeedbackBundle.message("notification.request.eap.feedback.action.respond.text")) {
        executeEAPFeedbackAction()
      }
    )

    @Suppress("DialogTitleCapitalization")
    notification.addAction(
      NotificationAction.createSimpleExpiring(
        EAPFeedbackBundle.message("notification.request.eap.feedback.action.dont.show.text")) {}
    )

    notification.notify(null)
  }
}

fun executeEAPFeedbackAction() {
  BrowserUtil.browse(application.service<EAPFeedbackUrlProvider>().surveyUrl(), null)
}

fun isEAPEnv(): Boolean {
  val app = ApplicationManager.getApplication()
  return !app.isUnitTestMode && !app.isHeadlessEnvironment() && app.isEAP
}

fun isEAPFeedbackAvailable(): Boolean {
  if (!Registry.`is`(eapFeedbackRegistryKey)) return false

  return getParseTimerStarted(PropertiesComponent.getInstance()) != -1L
}

fun setEAPFeedbackShown(propComponent: PropertiesComponent = PropertiesComponent.getInstance()) {
  propComponent.setValue(getTimerStartedKey(), (-1L).toString())
}

private fun getProductName(): @NlsSafe String = ApplicationNamesInfo.getInstance().productName
private fun getProductVersion(): @NlsSafe String = ApplicationInfo.getInstance().shortVersion

private fun getTimerStartedKey(): String {
  val productName = getProductName()
  return "$productName.$timerStartedKey.${getProductVersion()}"
}

private fun getParseTimerStarted(propComponent: PropertiesComponent): Long? {
  val value = propComponent.getValue(getTimerStartedKey()) ?: return null
  return try {
    value.toLong()
  }
  catch (e: NumberFormatException) {
    setEAPFeedbackShown(propComponent)
    -1
  }
}