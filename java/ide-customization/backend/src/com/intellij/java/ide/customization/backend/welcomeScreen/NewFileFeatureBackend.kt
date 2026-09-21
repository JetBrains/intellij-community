// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ide.customization.backend.welcomeScreen

import com.intellij.openapi.project.Project
import com.intellij.platform.ide.nonModalWelcomeScreen.backend.WelcomeScreenFeatureBackend

/**
 * Backs the "New File" button of the welcome right tab.
 *
 * `WelcomeNewEmptyFile` is the new file action of the welcome experience: it creates an `Untitled` file in the home
 * files root and opens it. The action reads the language from the data context, and this button states none, so the
 * file comes out as plain text.
 */
internal class NewFileFeatureBackend : WelcomeScreenFeatureBackend() {
  override val featureKey: String = "New.File"

  override fun onClick(project: Project) {
    invokeWelcomeScreenAction(project, "WelcomeNewEmptyFile")
  }
}
