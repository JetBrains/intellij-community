// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.session

import com.jediterm.terminal.CursorShape
import com.jediterm.terminal.emulator.mouse.MouseFormat
import com.jediterm.terminal.emulator.mouse.MouseMode
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class TerminalState(
  val isCursorVisible: Boolean,
  /** Null means default */
  val cursorShape: CursorShape?,
  val mouseMode: MouseMode,
  val mouseFormat: MouseFormat,
  val isAlternateScreenBuffer: Boolean,
  val isApplicationArrowKeys: Boolean,
  val isApplicationKeypad: Boolean,
  val isAutoNewLine: Boolean,
  val isAltSendsEscape: Boolean,
  val isBracketedPasteMode: Boolean,
  val windowTitle: String,
  /** Whether such events as command started/finished are supported by the shell integration */
  val isShellIntegrationEnabled: Boolean,
  val currentDirectory: String,
)