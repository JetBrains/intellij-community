// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.fs.createTemporaryFile
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.toEelApiBlocking
import com.intellij.platform.eel.provider.utils.EelPathUtils.isPathLocal
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

@ApiStatus.Internal
object EelProjectUtils {
  /**
   * **Obsolete – avoid it in new code.**
   *
   * Although positioned one level higher than *isPathLocal*, this helper still bakes in the same
   * local-vs-remote distinction, tying callers to a specific execution environment and reducing the
   * portability of higher-level logic.
   *
   * @return `true` when the project's default Eel target is local.
   */
  @ApiStatus.Obsolete
  @JvmStatic
  fun isProjectLocal(project: Project): Boolean {
    val projectFilePath = project.projectFilePath ?: return true
    return isPathLocal(Path.of(projectFilePath))
  }

  @JvmStatic
  @JvmOverloads
  @RequiresBackgroundThread(generateAssertion = false)
  fun createTemporaryFile(project: Project?, prefix: String = "", suffix: String = "", deleteOnExit: Boolean = true): Path {
    if (project == null || isProjectLocal(project)) {
      return Files.createTempFile(prefix, suffix)
    }

    val projectFilePath = project.projectFilePath ?: return Files.createTempFile(prefix, suffix)
    return runBlockingMaybeCancellable {
      val eel = Path.of(projectFilePath).getEelDescriptor().toEelApi()
      val file = eel.fs.createTemporaryFile().suffix(suffix).prefix(prefix).deleteOnExit(deleteOnExit).getOrThrowFileSystemException()
      file.asNioPath()
    }
  }

  @JvmStatic
  @JvmOverloads
  @OptIn(ExperimentalPathApi::class)
  @RequiresBackgroundThread(generateAssertion = false)
  fun createTemporaryDirectory(project: Project?, prefix: String = "", suffix: String = "", deleteOnExit: Boolean = false): Path {
    val projectFilePath = project?.projectFilePath
    if (project == null || isProjectLocal(project) || projectFilePath == null) {
      val dir = Files.createTempDirectory(prefix)
      if (deleteOnExit) {
        Runtime.getRuntime().addShutdownHook(Thread {
          dir.deleteRecursively()
        })
      }
      return dir
    }
    val eel = Path.of(projectFilePath).getEelDescriptor().toEelApiBlocking()
    return EelPathUtils.createTemporaryDirectory(eel, prefix, suffix, deleteOnExit)
  }
}
