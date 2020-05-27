// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Path

/**
 * Represents created target environment. It might be local machine,
 * local or remote Docker container, SSH machine or any other machine
 * that is able to run processes on it.
 */
@ApiStatus.Experimental
abstract class TargetEnvironment(
  open val request: TargetEnvironmentRequest
) {

  sealed class RemotePath {
    data class Persistent(val absolutePath: String) : RemotePath()

    data class Temporary @JvmOverloads constructor(
      /** Any string. An environment implementation may reuse previously created directories for the same hint. */
      val hint: String? = null,

      /** If null, use `/tmp` or something similar, autodetected. */
      val parentDirectory: String? = null
    ) : RemotePath()
  }

  data class UploadRoot @JvmOverloads constructor(
    val localRootPath: Path,

    val remoteRootPath: RemotePath,

    /**
     * If true, IDE should try to remove the directory when [shutdown] is being called.
     * TODO maybe get rid of it? It causes a race between two environments using the same upload root.
     */
    val removeAtShutdown: Boolean = false
  )

  data class DownloadRoot(
    /**
     * A certain path on the local machine or null for creating a temporary directory.
     * The temporary directory should be deleted with [shutdown].
     */
    val localRootPath: Path?,

    /** TODO Should [Temprorary] paths with the same hint point on the same directory for uploads and downloads? */
    val remoteRootPath: RemotePath
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
    fun upload(relativePath: String, progressIndicator: ProgressIndicator): String
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

  abstract val remotePlatform: TargetPlatform

  //FIXME: document
  abstract fun shutdown()

}