// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ide.customization.backend.welcomeScreen

import com.intellij.openapi.project.Project
import com.intellij.platform.ide.nonModalWelcomeScreen.backend.WelcomeScreenFeatureBackend

/**
 * Backs the "Open File" button of the welcome right tab. The button runs `OpenFile`, which shows the file chooser.
 */
internal class OpenFileFeatureBackend : WelcomeScreenFeatureBackend() {
  override val featureKey: String = "Open.File"

  override fun onClick(project: Project) {
    invokeWelcomeScreenAction(project, "OpenFile")
  }
}
