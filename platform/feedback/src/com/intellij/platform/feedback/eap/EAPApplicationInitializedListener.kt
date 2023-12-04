// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.eap

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationAction
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.feedback.impl.notification.RequestFeedbackNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private const val timerStartedKey = "eap.feedback.scheduled"
private const val eapFeedbackRegistryKey = "eap.feedback.notification.enabled"

class EAPApplicationInitializedListener : ApplicationInitializedListener {
  init {
    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode ||
        app.isHeadlessEnvironment() ||
        !app.isEAP) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(asyncScope: CoroutineScope) {
    asyncScope.launch {
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
    if (parsedValue == -1) return

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
    setShown(propComponent)
    showNotification()
    return
  }

  private fun showNotification() {
    val notification = RequestFeedbackNotification(
      "Feedback In IDE",
      EAPFeedbackBundle.message("notification.request.eap.feedback.title"),
      EAPFeedbackBundle.message("notification.request.eap.feedback.text", getProductName())
    )

    @Suppress("DialogTitleCapitalization")
    notification.addAction(
      NotificationAction.createSimpleExpiring(
        EAPFeedbackBundle.message("notification.request.eap.feedback.action.respond.text")) {
        BrowserUtil.browse(
          " https://surveys.jetbrains.com/s3/${getProductName().lowercase()}-${
            getProductVersion().replace('.', '-')
          }-eap-user-survey",
          null)
      }
    )

    @Suppress("DialogTitleCapitalization")
    notification.addAction(
      NotificationAction.createSimpleExpiring(
        EAPFeedbackBundle.message("notification.request.eap.feedback.action.dont.show.text")) {}
    )

    notification.notify(null)
  }

  private fun getProductVersion(): @NlsSafe String = ApplicationInfo.getInstance().shortVersion

  private fun getParseTimerStarted(propComponent: PropertiesComponent): Int? {
    val value = propComponent.getValue(getTimerStartedKey()) ?: return null
    return try {
      value.toInt()
    }
    catch (e: NumberFormatException) {
      setShown(propComponent)
      -1
    }
  }

  private fun setShown(propComponent: PropertiesComponent) {
    propComponent.setValue(getTimerStartedKey(), (-1).toString())
  }

  private fun getTimerStartedKey(): String {
    val productName = getProductName()
    return "$productName.$timerStartedKey.${getProductVersion()}"
  }

  private fun getProductName(): @NlsSafe String = ApplicationNamesInfo.getInstance().productName
}
