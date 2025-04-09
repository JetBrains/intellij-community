// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.configurations.PathEnvironmentVariableUtil.getPathDirs
import com.intellij.execution.process.LocalProcessService
import com.intellij.execution.process.LocalPtyOptions
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.EelResult
import com.intellij.platform.eel.impl.fs.EelProcessResultImpl
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.util.EnvironmentUtil
import com.pty4j.PtyProcess
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.*

private val LOG = Logger.getInstance(EelLocalExecApi::class.java)

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
            LocalEelProcess(LocalProcessService.getInstance().startPtyProcess(
              listOf(builder.exe) + args,
              builder.workingDirectory?.toString(),
              environment,
              LocalPtyOptions.defaults().builder().also {
                it.consoleMode(!p.echo)
                it.initialColumns(p.columns)
                it.initialRows(p.rows)
              }.build(),
              false,
            ) as PtyProcess)
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

  override suspend fun findExeFilesInPath(binaryName: String): List<EelPath> {
    val result = if (binaryName.contains('/') || binaryName.contains('\\')) {
      val absolutePath = Path(binaryName)
      if (!absolutePath.isAbsolute) {
        LOG.warn("Should be either absolute path to executable of plain filename to search, got: $binaryName")
        emptyList()
      }
      else {
        val pathsToProbe = mutableListOf(absolutePath)
        if (SystemInfo.isWindows) {
          pathsToProbe.addAll(PathEnvironmentVariableUtil.getWindowsExecutableFileExtensions().map { ext -> Path(binaryName + ext) })
        }
        pathsToProbe.filter { exeFile -> exeFile.isRegularFile() && exeFile.isExecutable() }
      }
    }
    else {
      val pathEnvVarValue = PathEnvironmentVariableUtil.getPathVariableValue()
      val pathDirs = pathEnvVarValue?.let { getPathDirs(pathEnvVarValue) }?.map { Path(it) }.orEmpty()
      val names = mutableListOf(binaryName)
      if (SystemInfo.isWindows) {
        names.addAll(PathEnvironmentVariableUtil.getWindowsExecutableFileExtensions().map { ext -> binaryName + ext })
      }
      mutableListOf<Path>().also { collector ->
        for (dir in pathDirs) {
          if (dir.isAbsolute && dir.isDirectory()) {
            for (name in names) {
              val exeFile = dir.resolve(name)
              if (exeFile.isRegularFile() && exeFile.isExecutable()) {
                collector.add(exeFile)
              }
            }
          }
        }
      }
    }
    return result.map { EelPath.parse(it.absolutePathString(), descriptor) }
  }
}