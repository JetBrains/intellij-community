// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.logsUploader

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName.Companion.create
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * This extension point allows the log packer to include which additional files/directories when the _Collect logs_ action is called.
 */
@ApiStatus.Internal
interface LogProvider {
  companion object {
    val EP: ExtensionPointName<LogProvider> = create("com.intellij.logsPreprocessor")
  }

  fun getAdditionalLogFiles(project: Project?): List<LogsEntry>

  /**
   * Defines the directory name inside the archive and the files/directories to be added to it.
   */
  data class LogsEntry @JvmOverloads constructor(
    val entryName: String,
    val files: List<Path>,

    /**
     * If `true` and `entryName` is not empty, files will be added to the "entryName/file.name/" folder.
     * If `false` and `entryName` is not empty, files will be added to the "entryName/" folder.
     * If `entryName` is empty, files will be added to the root folder (use with care, as it may lead to clashes).
     */
    val createSubdirectories: Boolean = true,
  )
}
