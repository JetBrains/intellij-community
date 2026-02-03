// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.DynamicBundle
import com.intellij.ide.IdeCoreBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.l10n.LocalizationStateService
import com.intellij.l10n.LocalizationUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import java.util.*

private const val IGNORE_LANGUAGE_DETECTOR_PROPERTY_NAME = "LANGUAGE_DETECTOR_ASKED_BEFORE"
private const val SWITCH_BACK_LANGUAGE_DETECTOR_PROPERTY_NAME = "LANGUAGE_DETECTOR_ASKED_AFTER"

internal class LanguagePluginDetectionStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    val application = ApplicationManagerEx.getApplicationEx()
    val propertiesComponent = PropertiesComponent.getInstance()
    if (application == null
        || application.isUnitTestMode
        || application.isHeadlessEnvironment
        || propertiesComponent.ignoreLanguageDetector) {
      return
    }

    if (propertiesComponent.switchBackLanguage) {
      propertiesComponent.switchBackLanguage = false
      propertiesComponent.ignoreLanguageDetector = true

      val name = getLanguageName(LocalizationUtil.findLanguageBundle())

      @Suppress("HardCodedStringLiteral")
      NotificationGroupManager.getInstance()
        .getNotificationGroup("Language Plugins Notifications")
        .createNotification(ApplicationBundle.message("notification.content.enabled", name),
                            NotificationType.INFORMATION)
        .addAction(
          NotificationAction.create("Switch back to English") { _, notification ->
            finishSetLocale(notification, Locale.ENGLISH.toLanguageTag(), false)
          }
        )
        .notify(project)
      return
    }

    if (LocalizationUtil.getLocaleOrNullForDefault() != null) {
      return
    }

    val bundleEP = findLanguageEP()
    if (bundleEP != null) {
      val name = Locale.forLanguageTag(bundleEP.locale).getDisplayLanguage(Locale.ENGLISH)
      showNotification(
        project, getLanguageName(bundleEP),
        NotificationAction.create(ApplicationBundle.message("notification.content.switch.restart", name)) { _, notification ->
          finishSetLocale(notification, bundleEP.locale, true)
        }
      )
    }
  }
}

private fun showNotification(project: Project, name: String, action: NotificationAction) {
  NotificationGroupManager.getInstance()
    .getNotificationGroup("Language Plugins Notifications")
    .createNotification(ApplicationBundle.message("notification.content.available", name),
                        NotificationType.INFORMATION)
    .setSuggestionType(true)
    .addAction(action)
    .addAction(
      NotificationAction.create(IdeCoreBundle.message("dialog.options.do.not.ask")) { _, notification ->
        PropertiesComponent.getInstance().ignoreLanguageDetector = true
        notification.expire()
      }
    ).notify(project)
}

private fun getLanguageName(bundleEP: DynamicBundle.LanguageBundleEP?): String =
  bundleEP?.displayName ?: Locale.getDefault().getDisplayLanguage(Locale.ENGLISH)

private fun findLanguageEP(): DynamicBundle.LanguageBundleEP? {
  val default = Locale.getDefault()
  val locale = when (default.language) {
    "zh" -> {
      if (!Locale.SIMPLIFIED_CHINESE.country.equals(default.country)) {
        return null
      }
      Locale.SIMPLIFIED_CHINESE
    }
    "ja" -> Locale.JAPANESE
    "ko" -> Locale.KOREAN
    else -> default
  }
  return LocalizationUtil.findLanguageBundle(locale)
}

private fun finishSetLocale(notification: Notification, locale: String, switchBackLanguage: Boolean) {
  LocalizationStateService.getInstance()!!.setSelectedLocale(locale)
  PropertiesComponent.getInstance().switchBackLanguage = switchBackLanguage
  notification.expire()
  ApplicationManagerEx.getApplicationEx().restart(true)
}

private var PropertiesComponent.ignoreLanguageDetector: Boolean
  get() = getBoolean(IGNORE_LANGUAGE_DETECTOR_PROPERTY_NAME, false)
  set(value) = setValue(IGNORE_LANGUAGE_DETECTOR_PROPERTY_NAME, value)

private var PropertiesComponent.switchBackLanguage: Boolean
  get() = getBoolean(SWITCH_BACK_LANGUAGE_DETECTOR_PROPERTY_NAME, false)
  set(value) = setValue(SWITCH_BACK_LANGUAGE_DETECTOR_PROPERTY_NAME, value)