// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalProcessAuthHelper

import com.intellij.openapi.components.Service
import com.intellij.openapi.util.NlsSafe
import externalApp.ExternalAppUtil
import externalApp.nativessh.NativeSshAskPassApp
import externalApp.nativessh.NativeSshAskPassAppHandler
import org.jetbrains.annotations.NonNls
import java.util.*

@Service
class NativeSshAuthService : ExternalProcessHandlerService<NativeSshAuthenticator>("intellij-ssh-askpass",
                                                                                   NativeSshAskPassAppHandler.HANDLER_NAME,
                                                                                   NativeSshAskPassApp::class.java) {
  override fun createRpcRequestHandlerDelegate(): Any {
    return InternalRequestHandler()
  }

  /**
   * Internal handler implementation class, do not use it.
   */
  inner class InternalRequestHandler : NativeSshAskPassAppHandler {
    override fun handleInput(handlerNo: @NonNls String, description: @NlsSafe String): String {
      val g = getHandler(UUID.fromString(handlerNo))
      val answer = g.handleInput(description)
      return ExternalAppUtil.adjustNullTo(answer)
    }
  }
}