// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.pty

import com.jediterm.core.util.TermSize
import com.jediterm.terminal.ProcessTtyConnector
import com.jediterm.terminal.TtyConnectorResizeStrategy
import com.jediterm.terminal.TtyConnectorResizeStrategyProvider
import com.pty4j.PtyProcess
import com.pty4j.WinSize
import com.pty4j.windows.conpty.WinConPtyProcess
import java.nio.charset.Charset

open class PtyProcessTtyConnector @JvmOverloads constructor(
  private val process: PtyProcess,
  charset: Charset,
  commandLine: List<String>? = null,
) : ProcessTtyConnector(process, charset, commandLine), TtyConnectorResizeStrategyProvider {
  override val resizeStrategy: TtyConnectorResizeStrategy = process.getTtyConnectorResizeStrategy()

  override fun resize(termSize: TermSize) {
    if (isConnected) {
      process.winSize = WinSize(termSize.columns, termSize.rows)
    }
  }

  @Suppress("HardCodedStringLiteral")
  override fun getName(): String = "Local"
}

/**
 * We have to use [TtyConnectorResizeStrategy.POSTPONED] for [WinConPtyProcess]
 * because it tends to make full-screen update on resize which may lead to screen corruption
 * if applied immediately.
 */
internal fun Process.getTtyConnectorResizeStrategy(): TtyConnectorResizeStrategy {
  return if (this is WinConPtyProcess) {
    TtyConnectorResizeStrategy.POSTPONED
  }
  else TtyConnectorResizeStrategy.IMMEDIATE
}