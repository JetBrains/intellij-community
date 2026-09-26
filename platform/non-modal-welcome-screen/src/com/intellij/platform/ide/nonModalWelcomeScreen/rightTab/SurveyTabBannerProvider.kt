// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.nonModalWelcomeScreen.NonModalWelcomeScreenBundle
import com.intellij.util.PlatformUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.JComponent
import kotlin.time.Duration.Companion.milliseconds

private const val HIDE_KEY = "projectless.survey.hide"
private const val TIME_KEY = "projectless.survey.time"
private const val DAY: Long = 24 * 3600 * 1000

internal class SurveyTabBannerProvider : WelcomeScreenRightTabBannerProvider {
  override fun isApplicable(project: Project): Boolean {
    val properties = PropertiesComponent.getInstance()
    if (properties.getBoolean(HIDE_KEY) || getUrlProductCode().isEmpty()) {
      return false
    }

    if (properties.getLong(TIME_KEY, 0) == 0L) {
      properties.setValue(TIME_KEY, System.currentTimeMillis().toString())
    }
    return true
  }

  override fun createBanner(project: Project): JComponent {
    var timer: Job? = null
    val banner = object : WelcomeScreenBannerComponent(), Disposable {
      override fun dispose() {
        timer?.cancel()
        timer = null
      }
    }

    val properties = PropertiesComponent.getInstance()
    val showTime = properties.getLong(TIME_KEY, 0) + DAY
    val currentTime = System.currentTimeMillis()
    val timeDelta = showTime - currentTime

    if (timeDelta > 0) {
      banner.isVisible = false

      val provider = WelcomeRightTabContentProvider.getSingleExtension()!!
      timer = provider.coroutineScope.launch {
        delay(timeDelta.milliseconds)
        timer = null
        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          banner.isVisible = true
          banner.revalidate()
          banner.repaint()
        }
      }
    }

    val messageKey =
      if (PlatformUtils.isIntelliJ() || PlatformUtils.isPhpStorm()) "projectless.survey.message.new" else "projectless.survey.message.old"
    banner.setMessage(NonModalWelcomeScreenBundle.message(messageKey, getUrlProductName()))
    banner.setIcon(AllIcons.Ide.Feedback)

    banner.setCloseAction {
      PropertiesComponent.getInstance().setValue(HIDE_KEY, true)
    }

    banner.addAction(NonModalWelcomeScreenBundle.message("projectless.survey.action")) {
      BrowserUtil.open("https://surveys.jetbrains.com/s3/projectless-state-survey?product=${getUrlProductCode()}")
      banner.removeFromParent()
    }

    return banner
  }

  private fun getUrlProductName(): String {
    if (PlatformUtils.isRider()) {
      return "Rider"
    }
    return ApplicationNamesInfo.getInstance().fullProductName
  }

  private fun getUrlProductCode(): String {
    return when {
      PlatformUtils.isIntelliJ() -> "IntelliJ+IDEA"
      PlatformUtils.isPyCharm() -> "PyCharm"
      PlatformUtils.isPhpStorm() -> "PhpStorm"
      PlatformUtils.isGoIde() -> "GoLand"
      PlatformUtils.isRider() -> "Rider"
      else -> ""
    }
  }
}