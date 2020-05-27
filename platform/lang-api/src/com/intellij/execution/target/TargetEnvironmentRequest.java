// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target;

import com.intellij.execution.Platform;
import com.intellij.execution.target.value.TargetValue;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A request for target environment.
 * <p>
 * Can be filled with the requirements for target environment like files to upload,
 * ports to bind and locations to imported from another environments.
 * <p>
 * Implementations must cancel promises of all created TargetValues
 */
@ApiStatus.Experimental
public interface TargetEnvironmentRequest {
  /**
   * @return a platform of the environment to be prepared.
   * The result heavily depends on the {@link TargetEnvironmentFactory#getTargetPlatform()}
   */
  @NotNull
  TargetPlatform getTargetPlatform();

  /**
   * Every target must support at least one non-configurable upload-only "default" volume, which may be used by the run configurations
   * as a last resort for ungrouped file uploads.
   */
  @NotNull
  Volume getDefaultVolume();

  /**
   * @return new, separate, upload-only volume at some unspecified remote location
   */
  @NotNull
  default Volume createTempVolume() {
    return createUploadRoot(null, true);
  }

  /**
   * @param temporary If true, volume should be deleted after calling
   *                  {@link TargetEnvironment#shutdown() TargetEnvironment.shutdown()}
   *                  of owning environment instance.
   */
  @NotNull
  Volume createUploadRoot(@Nullable String remoteRootPath, boolean temporary);

  @NotNull
  DownloadableVolume createDownloadRoot(@Nullable String remoteRootPath);

  //Iterable<? extends Volume> getVolumes();

  /**
   * Creates the requirement to open a port on the target environment.
   * <p>
   * Returned value may be used in {@link TargetedCommandLineBuilder}
   * where it will be replaced to the passed port.
   * <p>
   * As soon as target will be prepared, the value will also contain the port on local machine
   * that corresponds to the targetPort on target machine.
   */
  @NotNull
  TargetValue<Integer> bindTargetPort(int targetPort);

  /**
   * Creates the requirement to make a service listening on the provided port
   * on the local machine available for the process in the target environment.
   * <p>
   * The returned value contains the host and the port, which the target
   * process should connect to to access the local service.
   */
  @NotNull
  TargetValue<HostPort> bindLocalPort(int localPort);

  interface Volume {
    @NotNull
    Platform getPlatform();

    @NotNull
    String getVolumeId();

    /**
     * Creates the requirement to upload the local path up to the target environment.
     * <p/>
     * Returned value may be used in [TargetedCommandLineBuilder]
     * where it will be replaced to the corresponding **absolute** path at the target machine.
     */
    @NotNull
    TargetValue<String> createUpload(@NotNull String localPath);
  }

  interface DownloadableVolume extends Volume {
    @NotNull
    String getRemoteRoot();

    /**
     * Creates the requirement to download the local path from the target environment.
     * <p/>
     * Returned value has remote promise resolved to `getRemoteRoot().resolve(rootRelativePath).
     * Local value is a promise which is resolved just before environment termination, when the files are actually downloaded from
     * target to local machine.
     */
    @NotNull
    TargetValue<String> createDownload(@NotNull String rootRelativePath);
  }
}
