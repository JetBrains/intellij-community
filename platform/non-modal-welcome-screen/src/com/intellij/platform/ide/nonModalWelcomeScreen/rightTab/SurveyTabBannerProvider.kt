// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.nonModalWelcomeScreen.NonModalWelcomeScreenBundle
import com.intellij.util.PlatformUtils
import javax.swing.JComponent

private const val HIDE_KEY = "projectless.survey.hide"

internal class SurveyTabBannerProvider : WelcomeScreenRightTabBannerProvider {
  override fun isApplicable(project: Project): Boolean {
    return !PropertiesComponent.getInstance().getBoolean(HIDE_KEY) && getUrlProductCode().isNotEmpty()
  }

  override fun createBanner(project: Project): JComponent {
    val banner = WelcomeScreenBannerComponent()
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