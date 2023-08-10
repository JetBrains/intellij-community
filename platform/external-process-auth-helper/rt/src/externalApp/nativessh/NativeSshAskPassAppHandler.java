// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package externalApp.nativessh;

import externalApp.ExternalAppHandler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This handler is called by {@link NativeSshAskPassApp} when ssh requests user credentials.
 */
public interface NativeSshAskPassAppHandler extends ExternalAppHandler {

  @NonNls String IJ_SSH_ASK_PASS_HANDLER_ENV = "INTELLIJ_SSH_ASKPASS_HANDLER";
  @NonNls String IJ_SSH_ASK_PASS_PORT_ENV = "INTELLIJ_SSH_ASKPASS_PORT";
  @NonNls String ENTRY_POINT_NAME = "askPass";

  /**
   * Get the answer for interactive input request from ssh.
   *
   * @param description Key description specified by ssh, or empty string if description is not available
   * @return passphrase or null if prompt was canceled
   */
  @Nullable
  String handleInput(@NotNull String description);
}
