// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.pty

import com.intellij.execution.process.LocalProcessService
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.ProcessTtyConnector
import com.jediterm.terminal.TtyConnectorResizeStrategy
import com.jediterm.terminal.TtyConnectorResizeStrategyProvider
import java.nio.charset.Charset

open class PtyProcessTtyConnector @JvmOverloads constructor(
  process: Process,
  charset: Charset,
  commandLine: List<String>? = null,
) : ProcessTtyConnector(process, charset, commandLine), TtyConnectorResizeStrategyProvider {
  private val ptyControl = requireNotNull(LocalProcessService.getInstance().getPtyControl(process))
  override val resizeStrategy: TtyConnectorResizeStrategy = process.getTtyConnectorResizeStrategy()

  override fun resize(termSize: TermSize) {
    if (isConnected) {
      ptyControl.setWindowSize(termSize.columns, termSize.rows)
    }
  }

  @Suppress("HardCodedStringLiteral")
  override fun getName(): String = "Local"
}

/**
 * We have to use [TtyConnectorResizeStrategy.POSTPONED] for ConPTY processes
 * because it tends to make full-screen update on resize which may lead to screen corruption
 * if applied immediately.
 */
internal fun Process.getTtyConnectorResizeStrategy(): TtyConnectorResizeStrategy {
  return if (LocalProcessService.getInstance().getPtyControl(this)?.isConPty == true) {
    TtyConnectorResizeStrategy.POSTPONED
  }
  else TtyConnectorResizeStrategy.IMMEDIATE
}
