// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lang.lsWidget.impl

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.platform.lang.lsWidget.impl.fus.LanguageServiceWidgetActionKind
import com.intellij.platform.lang.lsWidget.impl.fus.LanguageServiceWidgetUsagesCollector
import com.intellij.platform.lang.lsWidget.internal.LanguageServiceWidgetActionsService

/**
 * This class is located in the `intellij.platform.lang.impl` module,
 * so it has access to the FUS-related [LanguageServiceWidgetUsagesCollector] class.
 * The service interface [LanguageServiceWidgetActionsService] is located in the `intellij.platform.lang` module,
 * so it doesn't have access to the FUS classes.
 */
private class LanguageServiceWidgetActionsServiceImpl : LanguageServiceWidgetActionsService() {
  override fun openWidgetItemRelatedSettings(project: Project, settingsPageClass: Class<out Configurable>) {
    LanguageServiceWidgetUsagesCollector.actionInvoked(project, LanguageServiceWidgetActionKind.OpenSettings, settingsPageClass)
    ShowSettingsUtil.getInstance().showSettingsDialog(project, settingsPageClass)
  }
}
