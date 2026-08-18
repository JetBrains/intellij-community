// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customization.java.welcomeScreen

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider
import java.nio.file.Path

private const val WELCOME_SCREEN_PROJECT_NAME: String = "IdeaWorkspace"

internal class IdeaWelcomeScreenProjectProvider : WelcomeScreenProjectProvider() {
  override fun canOpenFilesFromSystemFileManager(filePath: Path) = false

  override fun getWelcomeScreenProjectName() = WELCOME_SCREEN_PROJECT_NAME

  override fun doIsWelcomeScreenProject(project: Project) = project.name == WELCOME_SCREEN_PROJECT_NAME

  override fun doIsForceDisabledFileColors() = true

  override fun doGetCreateNewFileProjectPrefix() = "awesomeProject"
}