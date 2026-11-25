// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Opens several files as one unit. The platform asks each extension before it opens the files one by one.
 * The Open action asks [openFiles]. The system file manager and the command line ask [openExternalFiles].
 */
@ApiStatus.Internal
interface MultipleFileOpener {
  suspend fun openFiles(files: List<VirtualFile>, project: Project?): Boolean

  /**
   * Takes [files], two or more paths that come from outside the IDE at once: the system file manager or the command line.
   * [projectToClose] and [forceOpenInNewFrame] have the meaning of the same options of [OpenProjectTask].
   *
   * @return `null` to leave the paths to the platform, which opens each path on its own. An answer means that the
   * extension took all paths, also when it opened no project, and the platform opens nothing more.
   */
  suspend fun openExternalFiles(files: List<Path>, projectToClose: Project?, forceOpenInNewFrame: Boolean): ExternalAnswer? = null

  /** The answer of an extension that took the paths. [project] is the project that shows them, or `null` when none opened. */
  class ExternalAnswer(@JvmField val project: Project?)

  companion object {
    private val epName = ExtensionPointName.create<MultipleFileOpener>("com.intellij.multipleFileOpener")

    suspend fun openFiles(files: List<VirtualFile>, project: Project?): Boolean {
      return epName.extensionList.any { it.openFiles(files, project) }
    }

    suspend fun openExternalFiles(files: List<Path>, projectToClose: Project?, forceOpenInNewFrame: Boolean): ExternalAnswer? {
      return epName.extensionList.firstNotNullOfOrNull { it.openExternalFiles(files, projectToClose, forceOpenInNewFrame) }
    }
  }
}
