// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.protocolHandler

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.NlsContexts.DialogMessage
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface ProtocolNavigationCommandProcessor {
  companion object {
    val EP_NAME: ExtensionPointName<ProtocolNavigationCommandProcessor> = ExtensionPointName<ProtocolNavigationCommandProcessor>("com.intellij.protocolNavigationCommandProcessor")
  }

  /**
   * Processes the given raw URI and performs a specific operation.
   *
   * @return Returns true if the processing was successful and no further processing is required, otherwise false.
   */
  suspend fun execute(target: String?, parameters: Map<String, String>, fragment: String?): @DialogMessage String?
}