// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ide.customization.backend.welcomeScreen

import com.intellij.openapi.project.Project
import com.intellij.platform.ide.nonModalWelcomeScreen.backend.WelcomeScreenFeatureBackend

/**
 * Backs the "New Markdown File" button of the welcome right tab.
 *
 * `WelcomeMarkdownNewFile` is the Markdown variant of the welcome new file action: it creates an `Untitled` Markdown
 * file in the home files root and opens it. The action does nothing when no plugin supplies the Markdown language.
 */
internal class NewMarkdownFileFeatureBackend : WelcomeScreenFeatureBackend() {
  override val featureKey: String = "New.Markdown.File"

  override fun onClick(project: Project) {
    invokeWelcomeScreenAction(project, "WelcomeMarkdownNewFile")
  }
}
