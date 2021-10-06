// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl

import com.intellij.ide.CliResult
import com.intellij.ide.CommandLineCustomHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.JBProtocolCommand
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.thisLogger
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

class JetBrainsProtocolCommandLineHandler : CommandLineCustomHandler {
  override fun process(args: List<String>): Future<CliResult>? {
    val command = args[0]
    if (!command.startsWith(JBProtocolCommand.PROTOCOL)) return null

    val result = CompletableFuture<CliResult>()
    ApplicationManager.getApplication().invokeLater(
      {
        val commandResult = runCatching { JBProtocolCommand.execute(command) }
        ApplicationManager.getApplication().executeOnPooledThread {
          commandResult.mapCatching { it.get() }
            .onFailure { thisLogger().error(command, it) }
            .onSuccess { result.complete(if (it == null) CliResult.OK else CliResult(1, it)) }
        }
      },
      ModalityState.NON_MODAL)
    return result
  }
}
