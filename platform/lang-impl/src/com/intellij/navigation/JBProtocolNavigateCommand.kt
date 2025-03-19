// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.navigation

import com.intellij.ide.protocolHandler.ProtocolNavigationCommandProcessor
import com.intellij.openapi.application.JBProtocolCommand
import com.intellij.openapi.util.NlsContexts.DialogMessage
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
open class JBProtocolNavigateCommand : JBProtocolCommand(NAVIGATE_COMMAND) {
  /**
   * The handler parses the following "navigate" command parameters:
   *
   * \\?project=(?<project>[\\w]+)
   *   (&fqn[\\d]*=(?<fqn>[\\w.\\-#]+))*
   *   (&path[\\d]*=(?<path>[\\w-_/\\\\.]+)
   *     (:(?<lineNumber>[\\d]+))?
   *     (:(?<columnNumber>[\\d]+))?)*
   *   (&selection[\\d]*=
   *     (?<line1>[\\d]+):(?<column1>[\\d]+)
   *    -(?<line2>[\\d]+):(?<column2>[\\d]+))*
   */
  override suspend fun execute(target: String?, parameters: Map<String, String>, fragment: String?): @DialogMessage String? {
    return ProtocolNavigationCommandProcessor.EP_NAME.lazySequence().firstOrNull()?.execute(target, parameters, fragment)
  }
}