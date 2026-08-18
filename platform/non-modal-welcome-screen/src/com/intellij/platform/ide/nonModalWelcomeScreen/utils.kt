// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesService
import com.intellij.openapi.wm.ex.ProjectFrameCapability
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
const val NON_MODAL_WELCOME_SCREEN_SETTING_ID: String = "welcome.screen.non.modal.enabled"

@get:Internal
val isNonModalWelcomeScreenEnabled: Boolean
  get() = AdvancedSettings.getBoolean(NON_MODAL_WELCOME_SCREEN_SETTING_ID) && !System.getProperty("idea.force.disable.non.modal.welcome.screen")
    .toBoolean() && NonModalWelcomeScreenAccessProvider.isAvailable()

@Internal
suspend fun Project.isWelcomeExperienceProject(): Boolean {
  return ProjectFrameCapabilitiesService.getInstance().has(this, ProjectFrameCapability.WELCOME_EXPERIENCE)
}

@Internal
fun Project.isWelcomeExperienceProjectSync(): Boolean {
  @Suppress("DEPRECATION")
  return ProjectFrameCapabilitiesService.getInstanceSync().has(this, ProjectFrameCapability.WELCOME_EXPERIENCE)
}

@Internal
interface NonModalWelcomeScreenAccessProvider {
  companion object {
    private val EP_NAME: ExtensionPointName<NonModalWelcomeScreenAccessProvider> =
      ExtensionPointName("com.intellij.platform.ide.welcomeScreenAccessProvider")

    private fun getSingleExtension(): NonModalWelcomeScreenAccessProvider? {
      val providers = EP_NAME.extensionList
      if (providers.isEmpty()) return null
      if (providers.size > 1) {
        thisLogger().warn("Multiple NonModalWelcomeScreenAccessProvider extensions")
        return null
      }
      return providers.first()
    }

    internal fun isAvailable(): Boolean {
      return getSingleExtension()?.isAvailable() ?: true
    }
  }

  fun isAvailable(): Boolean
}