// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ide.customization.backend.welcomeScreen

import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.nonModalWelcomeScreen.backend.WelcomeScreenFeatureBackend

internal class IdeaPluginsWelcomeScreenFeature  : WelcomeScreenFeatureBackend() {
  override val featureKey: String = "plugins.settings"

  override fun onClick(project: Project) {
    PluginManagerConfigurable.showSettingsDialogFromWelcomeScreen(project)
  }
}