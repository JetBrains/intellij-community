// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local

import com.intellij.platform.eel.EelExecApi
import java.io.File
import java.io.IOException

class EelLocalExecApi : EelExecApi {
  override suspend fun execute(builder: EelExecApi.ExecuteProcessBuilder): EelExecApi.ExecuteProcessResult {
    assert(builder.pty == null) { "PTY isn't supported (yet)" }

    val jvmProcessBuilder = ProcessBuilder(builder.exe, *builder.args.toTypedArray()).apply {
      environment().putAll(builder.env)
      builder.workingDirectory?.let {
        directory(File(it))
      }
    }
    try {
      val process = jvmProcessBuilder.start()
      return EelExecApi.ExecuteProcessResult.Success(LocalEelProcess(process))
    }
    catch (e: IOException) {
      return EelExecApi.ExecuteProcessResult.Failure(-1, e.toString())
    }

  }

  override suspend fun fetchLoginShellEnvVariables(): Map<String, String> = System.getenv()
}