// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl

import com.intellij.ide.CliResult
import com.intellij.ide.CommandLineCustomHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.JBProtocolCommand
import com.intellij.openapi.application.JetBrainsProtocolHandler
import com.intellij.util.concurrency.FutureResult
import java.util.concurrent.Future

class JetBrainsProtocolCommandLineHandler : CommandLineCustomHandler {
  override fun process(args: List<String>): Future<CliResult>? {
    val command = args[0]
    if (command.startsWith(JetBrainsProtocolHandler.PROTOCOL)) {
      JetBrainsProtocolHandler.processJetBrainsLauncherParameters(command)
      ApplicationManager.getApplication().invokeLater { JBProtocolCommand.handleCurrentCommand() }
      return FutureResult(CliResult.OK)
    }
    return null
  }
}