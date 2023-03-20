// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalProcessAuthHelper

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import externalApp.nativessh.NativeSshAskPassApp
import externalApp.nativessh.NativeSshAskPassAppHandler

@Service(Service.Level.APP)
class NativeSshAuthService : ExternalProcessHandlerService<NativeSshAskPassAppHandler>(
  "intellij-ssh-askpass",
  NativeSshAskPassApp::class.java
) {
  companion object {
    @JvmStatic
    fun getInstance() = service<NativeSshAuthService>()
  }

  override fun handleRequest(handler: NativeSshAskPassAppHandler, requestBody: String): String? {
    return handler.handleInput(requestBody)
  }
}

class NativeSshExternalProcessRest : ExternalProcessRest<NativeSshAskPassAppHandler>(
  NativeSshAskPassAppHandler.ENTRY_POINT_NAME
) {
  override val externalProcessHandler: ExternalProcessHandlerService<NativeSshAskPassAppHandler> get() = service<NativeSshAuthService>()
}
