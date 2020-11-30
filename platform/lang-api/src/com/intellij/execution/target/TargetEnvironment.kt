// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.execution.ExecutionException
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.util.function.Predicate

/**
 * Represents created target environment. It might be local machine,
 * local or remote Docker container, SSH machine or any other machine
 * that is able to run processes on it.
 */
@ApiStatus.Experimental
abstract class TargetEnvironment(
  open val request: TargetEnvironmentRequest
) {

  sealed class TargetPath {
    /**
     * Request for a certain path on the target machine. If the [absolutePath] does not exist, it should be created.
     */
    data class Persistent(val absolutePath: String) : TargetPath()

    /**
     * Request for any not used random path.
     */
    data class Temporary @JvmOverloads constructor(
      /** Any string. An environment implementation may reuse previously created directories for the same hint. */
      val hint: String? = null,

      /** If null, use `/tmp` or something similar, autodetected. */
      val parentDirectory: String? = null
    ) : TargetPath()
  }

  data class UploadRoot @JvmOverloads constructor(
    val localRootPath: Path,

    val targetRootPath: TargetPath,

    /**
     * If true, IDE should try to remove the directory when [shutdown] is being called.
     * TODO maybe get rid of it? It causes a race between two environments using the same upload root.
     */
    val removeAtShutdown: Boolean = false
  ) {
    var volumeData: TargetEnvironmentType.TargetSpecificVolumeData? = null  // excluded from equals / hashcode
  }

  data class DownloadRoot(
    /**
     * A certain path on the local machine or null for creating a temporary directory.
     * The temporary directory should be deleted with [shutdown].
     */
    val localRootPath: Path?,

    /** TODO Should [Temprorary] paths with the same hint point on the same directory for uploads and downloads? */
    val targetRootPath: TargetPath
  )

  /** Target TCP port forwardings. */
  data class TargetPortBinding(
    val local: Int?,

    /** There is no robust way to get a random free port inside the Docker target. */
    val target: Int
  )

  /** Local TCP port forwardings. */
  data class LocalPortBinding(
    val local: Int,
    val target: Int?
  )

  /**
   * TODO Do we really need to have two different kinds of bind mounts?
   *  Docker and SSH provides bi-directional access to the target files.
   */
  interface UploadableVolume {
    val localRoot: Path

    val targetRoot: String

    /**
     * Upload `"$localRootPath/$relativePath"` to `"$targetRoot/$relativePath"`.
     * Returns the resulting remote path (even if it's predictable, many tests rely on specific, usually relative paths).
     */
    @Throws(IOException::class)
    fun upload(relativePath: String, targetProgressIndicator: TargetEnvironmentAwareRunProfileState.TargetProgressIndicator): String
  }

  interface DownloadableVolume {  // TODO Would it be better if there is no inheritance from the upload Volume?
    /* The only difference from the old [DownloadVolume]. */
    val localRoot: Path

    val targetRoot: String

    @Throws(IOException::class)
    fun download(relativePath: String, progressIndicator: ProgressIndicator)
  }

  open val uploadVolumes: Map<UploadRoot, UploadableVolume>
    get() = throw UnsupportedOperationException()

  open val downloadVolumes: Map<DownloadRoot, DownloadableVolume>
    get() = throw UnsupportedOperationException()

  /** Values are local ports. */
  open val targetPortBindings: Map<TargetPortBinding, Int>
    get() = throw UnsupportedOperationException()

  /** Values are local ports. */
  open val localPortBindings: Map<LocalPortBinding, HostPort>
    get() = throw UnsupportedOperationException()

  // TODO There are planned further modifications related to this method:
  //  1. Get rid of any `Promise` in `TargetedCommandLine`.
  //     Likely, there will be a completely new class similar to the `GeneralCommandLine` with adapter from `TargetedCommandLine`.
  //  2. Call of this method should consume the environment, i.e. there will be no need in the `shutdown` method.
  //     Therefore, to indicate the disposable nature of environments, the method might be moved to the `TargetEnvironmentFactory`.
  @Throws(ExecutionException::class)
  abstract fun createProcess(commandLine: TargetedCommandLine,
                             indicator: ProgressIndicator): Process

  abstract val targetPlatform: TargetPlatform

  //FIXME: document
  abstract fun shutdown()

  companion object {
    private fun getExcludedFromProjectChildren(localRoot: VirtualFile, project: Project): List<File>? {
      val result = mutableListOf<File>()
      for (module in ModuleManager.getInstance(project).modules) {
        for (excludeRoot in ModuleRootManager.getInstance(module).excludeRoots) {
          if (VfsUtil.isAncestor(localRoot, excludeRoot, true)) {
            result.add(excludeRoot.toNioPath().toFile())
          }
        }
      }
      return if (result.isEmpty()) null else result
    }

    fun getUploadFileFilter(localRoot: File, project: Project?): Predicate<File>? {
      if (project == null) return null
      val virtualFile = VfsUtil.findFileByIoFile(localRoot, false)
      if (virtualFile == null) return null
      val result = getExcludedFromProjectChildren(virtualFile, project)
      return object : Predicate<File> {
        val manager = FileTypeManager.getInstance()

        override fun test(file: File): Boolean {
          if (file == localRoot) return true
          if (Project.DIRECTORY_STORE_FOLDER == file.name) return false
          if (result != null && result.contains(file)) return false
          if (manager.isFileIgnored(file.name)) return false
          return true
        }
      }
    }
  }
}