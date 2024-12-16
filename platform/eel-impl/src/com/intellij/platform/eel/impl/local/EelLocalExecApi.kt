// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local

import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.EelResult
import com.intellij.platform.eel.impl.fs.EelProcessResultImpl
import com.pty4j.PtyProcessBuilder
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.io.IOException

@ApiStatus.Internal
class EelLocalExecApi : EelExecApi {
  override suspend fun execute(builder: EelExecApi.ExecuteProcessOptions): EelResult<EelProcess, EelExecApi.ExecuteProcessError> {
    val args = builder.args.toTypedArray()
    val pty = builder.pty

    val process: LocalEelProcess =
      try {
        // Inherit env vars, because lack of `PATH` might break things
        var environment = System.getenv().toMutableMap()
        environment.putAll(builder.env)
        if (pty != null) {
          // when emulating tty, TERM must be set either by caller or by us (for remote side it is done by ijent)
          if ("TERM" !in environment) {
            environment.getOrPut("TERM") { "xterm" }
          }
          LocalEelProcess(PtyProcessBuilder()
                            .setConsole(true)
                            .setCommand(arrayOf(builder.exe) + args)
                            .setEnvironment(environment)
                            .setDirectory(builder.workingDirectory)
                            .setInitialColumns(pty.columns)
                            .setInitialRows(pty.rows)
                            .start())
        }
        else {
          LocalEelProcess(ProcessBuilder(builder.exe, *args).apply {
            environment().putAll(environment)
            builder.workingDirectory?.let {
              directory(File(it))
            }
          }.start())
        }
      }
      catch (e: IOException) {
        return EelProcessResultImpl.createErrorResult(-1, e.toString())
      }
    return EelProcessResultImpl.createOkResult(process)
  }

  override suspend fun fetchLoginShellEnvVariables(): Map<String, String> = System.getenv()
}