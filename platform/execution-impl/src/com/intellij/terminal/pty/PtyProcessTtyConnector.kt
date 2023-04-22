// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.pty

import com.jediterm.core.util.TermSize
import com.jediterm.terminal.ProcessTtyConnector
import com.pty4j.PtyProcess
import com.pty4j.WinSize
import java.nio.charset.Charset

open class PtyProcessTtyConnector @JvmOverloads constructor(
  private val process: PtyProcess,
  charset: Charset,
  commandLine: List<String>? = null
) : ProcessTtyConnector(process, charset, commandLine) {
  override fun resize(termSize: TermSize) {
    if (isConnected) {
      process.winSize = WinSize(termSize.columns, termSize.rows)
    }
  }

  @Suppress("HardCodedStringLiteral")
  override fun getName(): String = "Local"
}
