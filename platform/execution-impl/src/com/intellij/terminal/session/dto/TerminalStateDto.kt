// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.session.dto

import com.intellij.terminal.session.TerminalState
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class TerminalStateDto(
  val isCursorVisible: Boolean,
  val cursorShape: CursorShapeDto?,
  val mouseMode: MouseModeDto,
  val mouseFormat: MouseFormatDto,
  val isAlternateScreenBuffer: Boolean,
  val isApplicationArrowKeys: Boolean,
  val isApplicationKeypad: Boolean,
  val isAutoNewLine: Boolean,
  val isAltSendsEscape: Boolean,
  val isBracketedPasteMode: Boolean,
  val windowTitle: String,
  val isShellIntegrationEnabled: Boolean,
  val currentDirectory: String,
)

@ApiStatus.Internal
fun TerminalState.toDto(): TerminalStateDto {
  return TerminalStateDto(
    isCursorVisible = isCursorVisible,
    cursorShape = cursorShape?.toDto(),
    mouseMode = mouseMode.toDto(),
    mouseFormat = mouseFormat.toDto(),
    isAlternateScreenBuffer = isAlternateScreenBuffer,
    isApplicationArrowKeys = isApplicationArrowKeys,
    isApplicationKeypad = isApplicationKeypad,
    isAutoNewLine = isAutoNewLine,
    isAltSendsEscape = isAltSendsEscape,
    isBracketedPasteMode = isBracketedPasteMode,
    windowTitle = windowTitle,
    isShellIntegrationEnabled = isShellIntegrationEnabled,
    currentDirectory = currentDirectory,
  )
}

@ApiStatus.Internal
fun TerminalStateDto.toTerminalState(): TerminalState {
  return TerminalState(
    isCursorVisible = isCursorVisible,
    cursorShape = cursorShape?.toCursorShape(),
    mouseMode = mouseMode.toMouseMode(),
    mouseFormat = mouseFormat.toMouseFormat(),
    isAlternateScreenBuffer = isAlternateScreenBuffer,
    isApplicationArrowKeys = isApplicationArrowKeys,
    isApplicationKeypad = isApplicationKeypad,
    isAutoNewLine = isAutoNewLine,
    isAltSendsEscape = isAltSendsEscape,
    isBracketedPasteMode = isBracketedPasteMode,
    windowTitle = windowTitle,
    isShellIntegrationEnabled = isShellIntegrationEnabled,
    currentDirectory = currentDirectory,
  )
}