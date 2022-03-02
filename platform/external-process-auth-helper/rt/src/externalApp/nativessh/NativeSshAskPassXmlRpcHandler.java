// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package externalApp.nativessh;

import org.jetbrains.annotations.NotNull;

/**
 * This handler is called via XML RPC from {@link NativeSshAskPassApp} when ssh requests user credentials.
 */
public interface NativeSshAskPassXmlRpcHandler {

  String IJ_SSH_ASK_PASS_HANDLER_ENV = "INTELLIJ_SSH_ASKPASS_HANDLER";
  String IJ_SSH_ASK_PASS_PORT_ENV = "INTELLIJ_SSH_ASKPASS_PORT";
  String HANDLER_NAME = NativeSshAskPassXmlRpcHandler.class.getName();
  String RPC_METHOD_NAME = HANDLER_NAME + ".handleInput";

  /**
   * Get the answer for interactive input request from ssh.
   *
   * @param handlerNo   Handler uuid passed via {@link #IJ_SSH_ASK_PASS_HANDLER_ENV}
   * @param description Key description specified by ssh, or empty string if description is not available
   * @return passphrase or null if prompt was canceled
   * <p>
   * Return value should be wrapped using {@link externalApp.ExternalAppUtil#adjustNullTo}
   */
  @NotNull
  @SuppressWarnings("unused")
  String handleInput(@NotNull String handlerNo, @NotNull String description);
}
