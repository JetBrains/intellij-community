// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalProcessAuthHelper

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import externalApp.nativessh.NativeSshAskPassApp
import externalApp.nativessh.NativeSshAskPassAppHandler
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.APP)
class NativeSshAuthService(
  coroutineScope: CoroutineScope
) : ExternalProcessHandlerService<NativeSshAskPassAppHandler>(
  "intellij-ssh-askpass",
  NativeSshAskPassApp::class.java,
  NativeSshAskPassApp(),
  listOf(NativeSshAskPassAppHandler.IJ_SSH_ASK_PASS_HANDLER_ENV, NativeSshAskPassAppHandler.IJ_SSH_ASK_PASS_PORT_ENV),
  coroutineScope
) {
  companion object {
    @JvmStatic
    fun getInstance() = service<NativeSshAuthService>()
  }

  override fun handleRequest(handler: NativeSshAskPassAppHandler, requestBody: String): String? {
    return handler.handleInput(requestBody)
  }
}

internal class NativeSshExternalProcessRest : ExternalProcessRest<NativeSshAskPassAppHandler>(
  NativeSshAskPassAppHandler.ENTRY_POINT_NAME
) {
  override val externalProcessHandler: ExternalProcessHandlerService<NativeSshAskPassAppHandler> get() = NativeSshAuthService.getInstance()
}
