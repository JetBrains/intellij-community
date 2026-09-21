// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.terminal

import com.intellij.ide.welcomeScreen.WelcomeUtils
import com.intellij.openapi.project.Project
import com.intellij.util.SystemProperties
import org.jetbrains.plugins.terminal.startup.MutableShellExecOptions
import org.jetbrains.plugins.terminal.startup.ShellExecOptionsCustomizer
import java.nio.file.Path

internal class WelcomeProjectTerminalCustomizer: ShellExecOptionsCustomizer {
  override fun customizeExecOptions(project: Project, shellExecOptions: MutableShellExecOptions) {
  }

  override fun getDefaultStartWorkingDirectory(project: Project): Path? {
    if (WelcomeUtils.isWelcomeProject(project)) {
      return Path.of(SystemProperties.getUserHome())
    }
    return null
  }
}