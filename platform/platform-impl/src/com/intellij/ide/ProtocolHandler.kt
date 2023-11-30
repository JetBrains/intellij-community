// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

interface ProtocolHandler {
  companion object {
    /**
     * This exit code tells the platform that it shouldn't display the welcome screen,
     * because the handler wants to take full care of the UI (not applicable if URI is handled by an already running instance).
     */
    const val PLEASE_NO_UI: Int = -1

    /**
     * This exit code tells the platform that it should shut itself down (not applicable if URI is handled by an already running instance).
     */
    const val PLEASE_QUIT: Int = -2

    /**
     * This exit code tells the platform that it should not focus IDE windows, the handler will manage the focus
     * (applicable only if URI is handled by an already running instance).
     */
    const val PLEASE_DO_NOT_FOCUS: Int = -3
  }

  val scheme: String

  suspend fun process(query: String): CliResult
}