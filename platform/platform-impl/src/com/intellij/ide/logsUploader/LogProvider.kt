// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.logsUploader

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName.Companion.create
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Extension point that allows packer to know which additional files it should pack at which path when the Collect logs action is called
 */
@ApiStatus.Internal
interface LogProvider {
  companion object {
    val EP: ExtensionPointName<LogProvider> = create("com.intellij.logsPreprocessor")
  }

  fun getAdditionalLogFiles(project: Project?): List<LogsEntry>

  /**
   * Class defines the folder name and the files added to it
   */
  data class LogsEntry @JvmOverloads constructor(
    val entryName: String,
    val files: List<Path>,

    /**
     * If true and `entryName` is not empty, files will be added to the "entryName/file.name/" folder.
     * If false and `entryName` is not empty, files will be added to the "entryName/" folder.
     * If `entryName` is empty, files will be added to the root folder.
     */
    val createSubdirectories: Boolean = true,
  )
}
