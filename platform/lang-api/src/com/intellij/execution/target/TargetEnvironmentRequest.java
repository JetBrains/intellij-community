// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target;

import com.intellij.execution.target.value.TargetValue;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * A request for target environment.
 * <p>
 * Can be filled with the requirements for target environment like files to upload,
 * ports to bind and locations to imported from another environents.
 *
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
   * Creates the requirement to upload the local path up to the target environment.
   * <p>
   * Returned value may be used in {@link TargetedCommandLineBuilder}
   * where it will be replaced to the corresponding path at the target machine.
   */
  @NotNull
  TargetValue<String> createUpload(@NotNull String localPath);

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
}
