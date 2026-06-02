// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api.lsWidget

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.platform.lsp.api.LspClient
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class LspWidgetInternalService {

  abstract fun createShowErrorOutputAction(lspClient: LspClient): AnAction?

  abstract fun restartLspClient(lspClient: LspClient)

  abstract fun stopLspClient(lspClient: LspClient)


  internal companion object {
    fun getInstance(): LspWidgetInternalService = ApplicationManager.getApplication().service()
  }
}
