// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.logsUploader

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path

class DefaultLogsProcessor : LogProvider {
  override fun getAdditionalLogFiles(project: Project?): List<LogProvider.LogsEntry> {
    val ideLogDir = PathManager.getLogDir()
    val daemonLogDir = findDaemonLogDir()

    return listOfNotNull(
      LogProvider.LogsEntry("", listOf(ideLogDir)),
      daemonLogDir?.let { LogProvider.LogsEntry("Daemon", listOf(it)) },
    )
  }

  private fun findDaemonLogDir(): Path? {
    val daemonLogDir = Path.of(PathManager.getDefaultLogPathFor("Daemon"))
    return daemonLogDir.takeIf(Files::isDirectory)
  }
}
