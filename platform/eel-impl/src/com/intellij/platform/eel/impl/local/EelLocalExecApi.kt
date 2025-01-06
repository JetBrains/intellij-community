// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.EelResult
import com.intellij.platform.eel.impl.fs.EelProcessResultImpl
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.util.EnvironmentUtil
import com.pty4j.PtyProcessBuilder
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.io.IOException

@ApiStatus.Internal
class EelLocalExecApi : EelExecApi {
  private companion object {
    /**
     * JVM on all OSes report IO error as `error=(code), (text)`. See `ProcessImpl_md.c` for Unix and Windows.
     */
    val errorPattern = Regex(".*error=(-?[0-9]{1,9}),.*")
  }

  override val descriptor: EelDescriptor
    get() = LocalEelDescriptor

  override suspend fun execute(builder: EelExecApi.ExecuteProcessOptions): EelResult<EelProcess, EelExecApi.ExecuteProcessError> {
    val args = builder.args.toTypedArray()
    val pty = builder.ptyOrStdErrSettings

    val process: LocalEelProcess =
      try {
        // Inherit env vars because lack of `PATH` might break things
        val environment = System.getenv().toMutableMap()
        environment.putAll(builder.env)
        when (val p = pty) {
          is EelExecApi.Pty -> {
            if ("TERM" !in environment) {
              environment.getOrPut("TERM") { "xterm" }
            }
            LocalEelProcess(PtyProcessBuilder()
                              .setConsole(true)
                              .setCommand(arrayOf(builder.exe) + args)
                              .setEnvironment(environment)
                              .setDirectory(builder.workingDirectory?.toString())
                              .setInitialColumns(p.columns)
                              .setInitialRows(p.rows)
                              .start())
          }
          EelExecApi.RedirectStdErr, null -> {
            LocalEelProcess(ProcessBuilder(builder.exe, *args).apply {
              environment().putAll(environment)
              redirectErrorStream(p != null)
              builder.workingDirectory?.let {
                directory(File(it.toString()))
              }
            }.start())
          }
        }
      }
      catch (e: IOException) {
        val errorCode = errorPattern.find(e.message ?: e.toString())?.let { result ->
          if (result.groupValues.size == 2) {
            try {
              result.groupValues[1].toInt()
            }
            catch (_: NumberFormatException) {
              null
            }
          }
          else {
            null
          }
        } ?: -3003 // Just a random code which isn't used by any OS and not zero
        return EelProcessResultImpl.createErrorResult(errorCode, e.toString())
      }
    return EelProcessResultImpl.createOkResult(process)
  }

  override suspend fun fetchLoginShellEnvVariables(): Map<String, String> = EnvironmentUtil.getEnvironmentMap()
}