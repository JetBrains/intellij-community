// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local

import com.intellij.platform.eel.EelExecApi
import com.pty4j.PtyProcessBuilder
import java.io.File
import java.io.IOException

class EelLocalExecApi : EelExecApi {
  override suspend fun execute(builder: EelExecApi.ExecuteProcessBuilder): EelExecApi.ExecuteProcessResult {
    val args = builder.args.toTypedArray()
    val pty = builder.pty

    val process: LocalEelProcess =
      try {
        if (pty != null) {
          LocalEelProcess(PtyProcessBuilder()
                            .setConsole(true)
                            .setCommand(arrayOf(builder.exe) + args)
                            .setEnvironment(builder.env)
                            .setDirectory(builder.workingDirectory)
                            .setInitialColumns(pty.columns)
                            .setInitialRows(pty.rows)
                            .start())
        }
        else {
          LocalEelProcess(ProcessBuilder(builder.exe, *args).apply {
            environment().putAll(builder.env)
            builder.workingDirectory?.let {
              directory(File(it))
            }
          }.start())
        }
      }
      catch (e: IOException) {
        return EelExecApi.ExecuteProcessResult.Failure(-1, e.toString())
      }
    return EelExecApi.ExecuteProcessResult.Success(process)
  }

  override suspend fun fetchLoginShellEnvVariables(): Map<String, String> = System.getenv()
}