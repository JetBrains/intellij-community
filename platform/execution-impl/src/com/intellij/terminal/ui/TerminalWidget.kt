// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.ui

import com.intellij.openapi.ui.ComponentContainer
import com.intellij.terminal.TerminalTitle
import com.jediterm.terminal.TtyConnector
import com.pty4j.WinSize
import org.jetbrains.annotations.Nls

interface TerminalWidget : ComponentContainer {
  val terminalTitle: TerminalTitle

  val windowSize: WinSize

  fun connectToTty(ttyConnector: TtyConnector)

  val ttyConnectorAccessor: TtyConnectorAccessor

  val ttyConnector: TtyConnector?
    get() = ttyConnectorAccessor.ttyConnector

  fun writePlainMessage(message: @Nls String)

  fun setCursorVisible(visible: Boolean)

  fun hasFocus(): Boolean

  fun requestFocus()

  fun addTerminationCallback(onTerminated: Runnable)
  fun removeTerminationCallback(onTerminated: Runnable)
}