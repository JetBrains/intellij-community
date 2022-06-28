// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.feedback.kotlinRejecters

import com.intellij.ide.feedback.kotlinRejecters.state.KotlinRejectersInfoService
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginEnableDisableAction
import com.intellij.openapi.application.ApplicationManager

fun recordKotlinPluginDisabling(descriptors: Collection<IdeaPluginDescriptor>,
                                action: PluginEnableDisableAction) {
  if (ApplicationManager.getApplication() != null && action.isDisable) {
    descriptors.find {
      it.pluginId.idString == "org.jetbrains.kotlin"
    } ?: return

    // Kotlin Plugin + 4 plugin dependency
    if (descriptors.size <= 5) {
      val kotlinRejectersInfoState = KotlinRejectersInfoService.getInstance().state
      kotlinRejectersInfoState.showNotificationAfterRestart = true
    }
  }
}