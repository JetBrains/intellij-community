// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.execution.ExecutionException
import com.intellij.openapi.progress.EmptyProgressIndicator
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

  sealed class TargetPath {
    /**
     * Request for a certain path on the target machine. If the [absolutePath] does not exist, it should be created.
     */
    data class Persistent(val absolutePath: String) : TargetPath()

    /**
     * Request for any not used random path.
     */
    class Temporary @JvmOverloads constructor(
      /** Any string. An environment implementation may reuse previously created directories for the same hint. */
      val hint: String? = null,

      /** Prefix for the randomly generated directory name */
      val prefix: String? = null,

      /** If null, use `/tmp` or something similar, autodetected. */
      val parentDirectory: String? = null
    ) : TargetPath() {
      override fun toString(): String = "Temporary(hint=$hint, prefix=$prefix, parentDirectory=$parentDirectory)"
      override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Temporary

        // instance with null hint considered unique thus differs from any other instance
        if (hint == null) return false
        if (hint != other.hint) return false
        if (prefix != other.prefix) return false
        if (parentDirectory != other.parentDirectory) return false

        return true
      }

      override fun hashCode(): Int {
        // instance with null hint considered unique
        if (hint == null) return super.hashCode()
        var result = hint.hashCode()
        result = 31 * result + (prefix?.hashCode() ?: 0)
        result = 31 * result + (parentDirectory?.hashCode() ?: 0)
        return result
      }
    }
  }

  /**
   * Mapping of localPath -> Something
   */
  interface MappingWithLocalPath {
    val localRootPath: Path
  }

  /**
   * Unonditional map between local and remote root.
   * Targets API do not create this mapping, it just exists
   */
  data class SynchronizedVolume(override val localRootPath: Path, val targetPath: String): MappingWithLocalPath

  data class UploadRoot @JvmOverloads constructor(
    override val localRootPath: Path,

    val targetRootPath: TargetPath,

    /**
     * If true, IDE should try to remove the directory when [shutdown] is being called.
     * TODO maybe get rid of it? It causes a race between two environments using the same upload root.
     */
    val removeAtShutdown: Boolean = false
  ): MappingWithLocalPath {
    var volumeData: TargetEnvironmentType.TargetSpecificVolumeData? = null  // excluded from equals / hashcode
  }

  data class DownloadRoot @JvmOverloads constructor(
    /**
     * A certain path on the local machine or null for creating a temporary directory.
     * The temporary directory should be deleted with [shutdown].
     */
    val localRootPath: Path?,

    /** TODO Should [Temprorary] paths with the same hint point on the same directory for uploads and downloads? */
    val targetRootPath: TargetPath,

    /**
     * If not null, target should try to persist the contents of the folder between the sessions,
     * and make it available for the next session requests with the same persistentId
     */
    val persistentId: String? = null
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

  interface Volume {
    val localRoot: Path

    val targetRoot: String

    /**
     * Returns the resulting remote path (even if it's predictable, many tests rely on specific, usually relative paths)
     * of uploading `"$localRootPath/$relativePath"` to `"$targetRoot/$relativePath"`.
     * Does not perform any kind of bytes transfer.
     */
    @Throws(IOException::class)
    fun resolveTargetPath(relativePath: String): String
  }

  /**
   * TODO Do we really need to have two different kinds of bind mounts?
   *  Docker and SSH provides bi-directional access to the target files.
   */
  interface UploadableVolume : Volume {

    /**
     * Upload `"$localRootPath/$relativePath"` to `"$targetRoot/$relativePath"`
     */
    @Throws(IOException::class)
    fun upload(relativePath: String,
               targetProgressIndicator: TargetProgressIndicator)
  }

  interface DownloadableVolume : Volume {

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

  open val localPortBindings: Map<LocalPortBinding, ResolvedPortBinding>
    get() = throw UnsupportedOperationException()

  // TODO There are planned further modifications related to this method:
  //  1. Get rid of any `Promise` in `TargetedCommandLine`.
  //     Likely, there will be a completely new class similar to the `GeneralCommandLine` with adapter from `TargetedCommandLine`.
  //  2. Call of this method should consume the environment, i.e. there will be no need in the `shutdown` method.
  //     Therefore, to indicate the disposable nature of environments, the method might be moved to the `TargetEnvironmentFactory`.
  @Throws(ExecutionException::class)
  abstract fun createProcess(commandLine: TargetedCommandLine,
                             indicator: ProgressIndicator = EmptyProgressIndicator()): Process

  abstract val targetPlatform: TargetPlatform

  //FIXME: document
  abstract fun shutdown()

  interface BatchUploader {
    fun canUploadInBatches(): Boolean

    @Throws(IOException::class)
    fun runBatchUpload(uploads: List<Pair<UploadableVolume, String>>,
                       targetProgressIndicator: TargetProgressIndicator)
  }
}