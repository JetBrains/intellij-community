// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customization.java.welcomeScreen

import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider
import com.intellij.platform.ide.nonModalWelcomeScreen.isNonModalWelcomeScreenEnabled
import java.nio.file.Files
import java.nio.file.Path

internal class IdeaWelcomeScreenProjectProvider : WelcomeScreenProjectProvider() {
  override fun canOpenFilesFromSystemFileManager(filePath: Path): Boolean {
    return isNonModalWelcomeScreenEnabled && Files.isRegularFile(filePath)
  }

  override fun doIsForceDisabledFileColors() = true

  override fun doGetCreateNewFileProjectPrefix() = "awesomeProject"

  override fun getToolWindowIdsToExclusiveShowing(): Set<String> {
    return setOf("Project", "Terminal")
  }
}
