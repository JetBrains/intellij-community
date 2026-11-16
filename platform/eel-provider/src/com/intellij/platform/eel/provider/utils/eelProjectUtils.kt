// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.fs.createTemporaryFile
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.toEelApiBlocking
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

@ApiStatus.Experimental
object EelProjectUtils {
  /**
   * **Obsolete – avoid it in new code.**
   *
   * Although positioned one level higher than *isPathLocal*, this helper still bakes in the same
   * local-vs-remote distinction, tying callers to a specific execution environment and reducing the
   * portability of higher-level logic.
   *
   * @return `true` when the project's Eel descriptor (see [getEelDescriptor]) is [LocalEelDescriptor].
   */
  @ApiStatus.Obsolete
  @JvmStatic
  fun isProjectLocal(project: Project): Boolean {
    return project.getEelDescriptor() == LocalEelDescriptor
  }

  /**
   * Creates a temporary file in the environment of the project's Eel descriptor (see [getEelDescriptor]),
   * or on the local machine when [project] is `null` or local.
   */
  @JvmStatic
  @JvmOverloads
  @Throws(IOException::class)
  @RequiresBackgroundThread(generateAssertion = false)
  fun createTemporaryFile(project: Project?, prefix: String = "", suffix: String = "", deleteOnExit: Boolean = true): Path {
    val descriptor = project?.getEelDescriptor() ?: LocalEelDescriptor
    if (descriptor == LocalEelDescriptor) {
      return Files.createTempFile(prefix, suffix)
    }

    return runBlockingMaybeCancellable {
      val eel = descriptor.toEelApi()
      val file = eel.fs.createTemporaryFile().suffix(suffix).prefix(prefix).deleteOnExit(deleteOnExit).getOrThrowFileSystemException()
      file.asNioPath()
    }
  }

  /**
   * Creates a temporary directory in the environment of the project's Eel descriptor (see [getEelDescriptor]),
   * or on the local machine when [project] is `null` or local.
   */
  @JvmStatic
  @JvmOverloads
  @Throws(IOException::class)
  @OptIn(ExperimentalPathApi::class)
  @RequiresBackgroundThread(generateAssertion = false)
  fun createTemporaryDirectory(project: Project?, prefix: String = "", suffix: String = "", deleteOnExit: Boolean = false): Path {
    val descriptor = project?.getEelDescriptor() ?: LocalEelDescriptor
    if (descriptor == LocalEelDescriptor) {
      val dir = Files.createTempDirectory(prefix)
      if (deleteOnExit) {
        Runtime.getRuntime().addShutdownHook(Thread {
          dir.deleteRecursively()
        })
      }
      return dir
    }
    return EelPathUtils.createTemporaryDirectory(descriptor.toEelApiBlocking(), prefix, suffix, deleteOnExit)
  }
}
