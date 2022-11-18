// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalProcessAuthHelper;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.util.NlsSafe;
import externalApp.ExternalAppUtil;
import externalApp.nativessh.NativeSshAskPassApp;
import externalApp.nativessh.NativeSshAskPassAppHandler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@Service
public final class NativeSshAuthService extends ExternalProcessHandlerService<NativeSshAuthenticator> {
  private NativeSshAuthService() {
    super("intellij-ssh-askpass", NativeSshAskPassAppHandler.HANDLER_NAME, NativeSshAskPassApp.class);
  }

  @NotNull
  @Override
  protected Object createRpcRequestHandlerDelegate() {
    return new InternalRequestHandler();
  }

  /**
   * Internal handler implementation class, do not use it.
   */
  public class InternalRequestHandler implements NativeSshAskPassAppHandler {
    @NotNull
    @Override
    public String handleInput(@NotNull @NonNls String handlerNo, @NotNull @NlsSafe String description) {
      NativeSshAuthenticator g = getHandler(UUID.fromString(handlerNo));
      String answer = g.handleInput(description);
      return ExternalAppUtil.adjustNullTo(answer);
    }
  }
}
