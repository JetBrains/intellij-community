// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.execution.Platform
import com.intellij.execution.target.value.TargetValue
import org.jetbrains.annotations.ApiStatus

/**
 * A request for target environment.
 *
 * Can be filled with the requirements for target environment like files to upload,
 * ports to bind and locations to imported from another environments.
 *
 * Implementations must cancel promises of all created TargetValues
 */
@ApiStatus.Experimental
interface TargetEnvironmentRequest {
  /**
   * @return a platform of the environment to be prepared.
   * The result heavily depends on the [TargetEnvironmentFactory.getTargetPlatform]
   */
  val targetPlatform: TargetPlatform

  /**
   * Set of required upload roots.
   * Note that both local and remote paths must be unique across all requests.
   * I.e., neither `setOf(UploadRoot("/local", Persistent("/remote1")), UploadRoot("/local", Persistent("/remote2")))`,
   * nor `setOf(UploadRoot("/local1", Persistent("/remote")), UploadRoot("/local2", Persistent("/remote")))` can be resolved.
   */
  @JvmDefault
  val uploadVolumes: MutableSet<TargetEnvironment.UploadRoot>
    get() = throw UnsupportedOperationException()

  /**
   * Set of required download roots.
   * Like for [uploadVolumes], both local and remote paths must be unique across all requests.
   */
  @JvmDefault
  val downloadVolumes: MutableSet<TargetEnvironment.DownloadRoot>
    get() = throw UnsupportedOperationException()

  /** Values are local ports. */
  @JvmDefault
  val targetPortBindings: MutableSet<TargetEnvironment.TargetPortBinding>
    get() = throw UnsupportedOperationException()

  @JvmDefault
  val localPortBindings: MutableSet<TargetEnvironment.LocalPortBinding>
    get() = throw UnsupportedOperationException()

  @JvmDefault
  fun duplicate(): TargetEnvironmentRequest = throw UnsupportedOperationException()

  /**
   * Every target must support at least one non-configurable upload-only "default" volume, which may be used by the run configurations
   * as a last resort for ungrouped file uploads.
   */
  @Deprecated("Use uploadVolumes")
  val defaultVolume: Volume

  /**
   * @return new, separate, upload-only volume at some unspecified remote location
   */
  @Deprecated("Use uploadVolumes")
  @JvmDefault
  fun createTempVolume(): Volume {
    return createUploadRoot(null, true)
  }

  /**
   * @param temporary If true, volume should be deleted after calling
   * [TargetEnvironment.shutdown()][TargetEnvironment.shutdown]
   * of owning environment instance.
   */
  @Deprecated("Use uploadVolumes")
  fun createUploadRoot(remoteRootPath: String?,
                       temporary: Boolean): Volume

  @Deprecated("Use downloadVolumes")
  fun createDownloadRoot(remoteRootPath: String?): DownloadableVolume

  /**
   * Creates the requirement to open a port on the target environment.
   *
   * Returned value may be used in [TargetedCommandLineBuilder]
   * where it will be replaced to the passed port.
   *
   * As soon as target will be prepared, the value will also contain the port on local machine
   * that corresponds to the targetPort on target machine.
   */
  @Deprecated("Use targetPortForwardings")
  fun bindTargetPort(targetPort: Int): TargetValue<Int>

  /**
   * Creates the requirement to make a service listening on the provided port
   * on the local machine available for the process in the target environment.
   * <p>
   * The returned value contains the host and the port, which the target
   * process should connect to to access the local service.
   */
  @Deprecated("Use localPortForwardings")
  fun bindLocalPort(localPort: Int): TargetValue<HostPort>

  @Deprecated("Use TargetEnvironment.UploadVolume")
  interface Volume {
    val platform: Platform
    val volumeId: String

    /**
     * Creates the requirement to upload the local path up to the target environment.
     *
     * Returned value may be used in [TargetedCommandLineBuilder]
     * where it will be replaced to the corresponding **absolute** path at the target machine.
     */
    fun createUpload(localPath: String): TargetValue<String>
  }

  @Deprecated("Use TargetEnvironment.DownloadVolume")
  interface DownloadableVolume : Volume {
    val remoteRoot: String

    /**
     * Creates the requirement to download the local path from the target environment.
     *
     * Returned value has remote promise resolved to `getRemoteRoot().resolve(rootRelativePath).
     * Local value is a promise which is resolved just before environment termination, when the files are actually downloaded from
     * target to local machine.
     */
    fun createDownload(rootRelativePath: String): TargetValue<String>
  }
}
