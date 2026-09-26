// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.terminal.backend

import com.intellij.java.terminal.shared.JavaTerminalSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.findDirectory
import com.intellij.openapi.vfs.toNioPathOrNull
import org.jetbrains.plugins.terminal.startup.MutableShellExecOptions
import org.jetbrains.plugins.terminal.startup.ShellExecOptionsCustomizer
import java.nio.file.Path

/** Customizer to override the JDK from the terminal with the project one */
internal class JdkCustomizer : ShellExecOptionsCustomizer {

  override fun customizeExecOptions(project: Project, shellExecOptions: MutableShellExecOptions) {
    if (!JavaTerminalSettings.instance.overrideJavaHome) return

    ProjectRootManager.getInstance(project).getProjectSdk()?.getSdkBinPath()?.let {
      shellExecOptions.setEnvironmentVariableToPath("JAVA_HOME", it.parent)
      shellExecOptions.prependEntryToPATH(it)
    }
  }
}

/** Attempt to get the JDK bin path the best it can */
private fun Sdk.getSdkBinPath(): Path? {
  if (sdkType.name != "JavaSDK") return null

  var path = this.homeDirectory?.findDirectory("bin")?.toNioPathOrNull()
  if (path == null) {
    this.homePath?.let { path = Path.of(it, "bin") }
  }
  return path
}
