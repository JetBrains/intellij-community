// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lang.lsWidget.internal

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * This class is located in the `intellij.platform.lang` module,
 * so it doesn't have access to FUS-related classes like
 * [com.intellij.platform.lang.lsWidget.impl.fus.LanguageServiceWidgetUsagesCollector].
 * The interface implementation ([com.intellij.platform.lang.lsWidget.impl.LanguageServiceWidgetActionsServiceImpl])
 * is located in the `intellij.platform.lang.impl` module, so it is able to log data to the FUS.
 */
@ApiStatus.Internal
abstract class LanguageServiceWidgetActionsService {

  abstract fun openWidgetItemRelatedSettings(project: Project, settingsPageClass: Class<out Configurable>)

  internal companion object {
    fun getInstance(): LanguageServiceWidgetActionsService = ApplicationManager.getApplication().service()
  }
}
