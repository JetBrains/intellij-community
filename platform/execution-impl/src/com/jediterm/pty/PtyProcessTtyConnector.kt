// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jediterm.pty

import com.intellij.terminal.pty.PtyProcessTtyConnector
import com.pty4j.PtyProcess
import org.jetbrains.annotations.ApiStatus
import java.nio.charset.Charset

@Deprecated("Use PtyProcessTtyConnector instead", ReplaceWith("com.intellij.terminal.pty.PtyProcessTtyConnector"))
@ApiStatus.ScheduledForRemoval
open class PtyProcessTtyConnector @JvmOverloads constructor(
  process: PtyProcess,
  charset: Charset,
  commandLine: List<String>? = null
) : PtyProcessTtyConnector(process, charset, commandLine)
