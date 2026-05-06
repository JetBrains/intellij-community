// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api.lsWidget

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.platform.lsp.api.LspServer
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class LspWidgetInternalService {

  abstract fun createShowErrorOutputAction(lspServer: LspServer): AnAction?

  abstract fun restartLspServer(lspServer: LspServer)

  abstract fun stopLspServer(lspServer: LspServer)


  internal companion object {
    fun getInstance(): LspWidgetInternalService = ApplicationManager.getApplication().service()
  }
}
