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
   * Every target must support at least one non-configurable upload-only "default" volume, which may be used by the run configurations
   * as a last resort for ungrouped file uploads.
   */
  val defaultVolume: Volume

  /**
   * @return new, separate, upload-only volume at some unspecified remote location
   */
  @JvmDefault
  fun createTempVolume(): Volume {
    return createUploadRoot(null, true)
  }

  /**
   * @param temporary If true, volume should be deleted after calling
   * [TargetEnvironment.shutdown()][TargetEnvironment.shutdown]
   * of owning environment instance.
   */
  fun createUploadRoot(remoteRootPath: String?,
                       temporary: Boolean): Volume

  fun createDownloadRoot(remoteRootPath: String?): DownloadableVolume

  //Iterable<? extends Volume> getVolumes();

  /**
   * Creates the requirement to open a port on the target environment.
   *
   * Returned value may be used in [TargetedCommandLineBuilder]
   * where it will be replaced to the passed port.
   *
   * As soon as target will be prepared, the value will also contain the port on local machine
   * that corresponds to the targetPort on target machine.
   */
  fun bindTargetPort(targetPort: Int): TargetValue<Int>

  /**
   * Creates the requirement to make a service listening on the provided port
   * on the local machine available for the process in the target environment.
   * <p>
   * The returned value contains the host and the port, which the target
   * process should connect to to access the local service.
   */
  fun bindLocalPort(localPort: Int): TargetValue<HostPort>

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
