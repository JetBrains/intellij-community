// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.logsUploader

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName.Companion.create
import com.intellij.openapi.project.Project
import java.io.File

/**
 * Extension point for defining the way to pack logs when Collect logs action is called
 */
interface LogsPreprocessor {
  companion object {
    val EP: ExtensionPointName<LogsPreprocessor> = create("com.intellij.logsPreprocessor")
  }

  fun getLogsEntries(project: Project?): List<LogsEntry>

  /**
   * Class defines the folder name and the files added to it
   */
  data class LogsEntry(
    val entryName: String,
    val files: List<File>
  )
}