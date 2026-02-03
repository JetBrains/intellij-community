// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asNioPath
import externalApp.ExternalAppEntry
import externalApp.ExternalCli
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.io.InputStream
import java.io.PrintStream
import java.nio.file.Path
import kotlin.io.path.pathString

private class EelExternalAppEntry(val process: EelExecApi.ExternalCliProcess) : ExternalAppEntry {
  val myStderr: PrintStream = process.stderr.asOutputStream().let(::PrintStream)
  val myStdout: PrintStream = process.stdout.asOutputStream().let(::PrintStream)
  val myStdin: InputStream = process.stdin.consumeAsInputStream()
  override fun getArgs(): Array<out String> = process.args.toTypedArray()
  override fun getEnvironment(): Map<String, String> = process.environment
  override fun getWorkingDirectory(): String = process.workingDir.asNioPath().pathString
  override fun getStderr(): PrintStream = myStderr
  override fun getStdout(): PrintStream = myStdout
  override fun getStdin(): InputStream = myStdin
  override fun getExecutablePath(): Path = process.executableName.asNioPath()
}

private fun EelExecApi.ExternalCliProcess.toExternalAppEntry(): ExternalAppEntry = EelExternalAppEntry(this)

@ApiStatus.Internal
suspend fun EelExecApi.serveExternalCli(coroutineScope: CoroutineScope, scriptBody: ExternalCli, options: EelExecApi.ExternalCliOptions): EelPath {
  val script = createExternalCli(options)
  coroutineScope.launch(start = CoroutineStart.ATOMIC) {
    script.consumeInvocations { process ->
      scriptBody.entryPoint(process.toExternalAppEntry())
    }
  }
  return script.path
}