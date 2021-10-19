// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl

import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.ide.ProtocolHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.JBProtocolCommand
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.util.NlsContexts.NotificationContent
import java.util.concurrent.CompletableFuture

class JetBrainsProtocolCommandLineHandler : ProtocolHandler {
  override fun getScheme(): String = JBProtocolCommand.SCHEME

  override fun process(query: String): CompletableFuture<@NotificationContent String?> {
    val result = CompletableFuture<String?>()
    ApplicationManager.getApplication().invokeLater(
      {
        val commandResult = runCatching { JBProtocolCommand.execute(query) }
        ProcessIOExecutorService.INSTANCE.execute {
          commandResult.mapCatching { it.get() }
            .onFailure { result.completeExceptionally(it) }
            .onSuccess { result.complete(it) }
        }
      },
      ModalityState.NON_MODAL)
    return result
  }
}
