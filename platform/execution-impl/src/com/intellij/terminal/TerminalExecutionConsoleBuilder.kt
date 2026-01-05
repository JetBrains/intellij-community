// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal

import com.intellij.openapi.project.Project
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.HyperlinkStyle

class TerminalExecutionConsoleBuilder(private val project: Project) {

  private var initialTermSize: TermSize = DEFAULT_INITIAL_TERM_SIZE
  private var settingsProvider: JBTerminalSystemSettingsProviderBase? = null
  private var convertLfToCrlfForProcessWithoutPty: Boolean = DEFAULT_CONVERT_LF_TO_CRLF_FOR_PROCESS_WITHOUT_PTY

  fun initialTermSize(initialTermSize: TermSize): TerminalExecutionConsoleBuilder = apply {
    this.initialTermSize = initialTermSize
  }

  fun settingsProvider(settingsProvider: JBTerminalSystemSettingsProviderBase): TerminalExecutionConsoleBuilder = apply {
    this.settingsProvider = settingsProvider
  }

  fun convertLfToCrlfForProcessWithoutPty(convertLfToCrlfForProcessWithoutPty: Boolean): TerminalExecutionConsoleBuilder = apply {
    this.convertLfToCrlfForProcessWithoutPty = convertLfToCrlfForProcessWithoutPty
  }

  fun build(): TerminalExecutionConsole {
    return TerminalExecutionConsole(
      project,
      initialTermSize,
      settingsProvider ?: createDefaultConsoleSettingsProvider(),
      convertLfToCrlfForProcessWithoutPty,
      null
    )
  }
}

@JvmField
internal val DEFAULT_INITIAL_TERM_SIZE: TermSize = TermSize(200, 24)

internal const val DEFAULT_CONVERT_LF_TO_CRLF_FOR_PROCESS_WITHOUT_PTY: Boolean = false

internal fun createDefaultConsoleSettingsProvider(): JBTerminalSystemSettingsProviderBase {
  return object : JBTerminalSystemSettingsProviderBase() {
    override fun getHyperlinkHighlightingMode(): HyperlinkStyle.HighlightMode = HyperlinkStyle.HighlightMode.ALWAYS
  }
}
