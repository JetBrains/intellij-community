// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen

import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesService
import com.intellij.openapi.wm.ex.ProjectFrameCapability
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
const val NON_MODAL_WELCOME_SCREEN_SETTING_ID: String = "welcome.screen.non.modal.enabled"

@get:Internal
val isNonModalWelcomeScreenEnabled: Boolean
  get() = AdvancedSettings.getBoolean(NON_MODAL_WELCOME_SCREEN_SETTING_ID) && !System.getProperty("idea.force.disable.non.modal.welcome.screen").toBoolean()

@Internal
suspend fun Project.isWelcomeExperienceProject(): Boolean {
  return ProjectFrameCapabilitiesService.getInstance().has(this, ProjectFrameCapability.WELCOME_EXPERIENCE)
}

internal fun Project.isWelcomeExperienceProjectSync(): Boolean {
  @Suppress("DEPRECATION")
  return ProjectFrameCapabilitiesService.getInstanceSync().has(this, ProjectFrameCapability.WELCOME_EXPERIENCE)
}
