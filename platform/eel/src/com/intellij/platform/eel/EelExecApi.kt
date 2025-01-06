// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import com.intellij.platform.eel.EelExecApi.PtyOrStdErrSettings
import com.intellij.platform.eel.path.EelPath
import org.jetbrains.annotations.CheckReturnValue

/**
 * Methods related to process execution: start a process, collect stdin/stdout/stderr of the process, etc.
 */
interface EelExecApi {

  val descriptor: EelDescriptor

  /**
   * Executes the process, returning either an [EelProcess] or an error provided by the remote operating system.
   *
   * The instance of the [ExecuteProcessOptions] _may_ become invalid after this call.
   *
   * The method may throw a RuntimeException only in critical cases like connection loss or a bug.
   *
   * See [executeProcessBuilder]
   */
  @CheckReturnValue
  suspend fun execute(builder: ExecuteProcessOptions): EelResult<EelProcess, ExecuteProcessError>

  /** Docs: [executeProcessBuilder] */
  interface ExecuteProcessOptions {
    val args: List<String>
    val env: Map<String, String>
    val ptyOrStdErrSettings: PtyOrStdErrSettings?
    val workingDirectory: EelPath?

    // TODO: Use EelPath as soon as it will be merged
    //  We cannot do it currently until IJPL-163265 is implemented
    val exe: String

    interface Builder {
      fun args(args: List<String>): Builder
      fun env(env: Map<String, String>): Builder

      /**
       * When set pty, be sure to accept esc codes for a terminal you are emulating.
       * This terminal should also be set in `TERM` environment variable, so setting it in [env] worth doing.
       * If not set, `xterm` will be used as a most popular one.
       *
       * See `termcap(2)`, `terminfo(2)`, `ncurses(3X)` and ISBN `0937175226`.
       */
      fun ptyOrStdErrSettings(pty: PtyOrStdErrSettings?): Builder
      fun workingDirectory(workingDirectory: EelPath?): Builder
      fun build(): ExecuteProcessOptions
    }

    companion object {
      /**
       * Creates builder to start a process on a local or remote machine.
       * stdin, stdout and stderr of the process are always forwarded, if there are.
       *
       * Beware that processes with [ExecuteProcessOptions.ptyOrStdErrSettings] usually don't have stderr.
       * The [EelProcess.stderr] must be an empty stream in such case.
       *
       * By default, environment is always inherited, which may be unwanted. [ExecuteProcessOptions.env] allows
       * to alter some environment variables, it doesn't clear the variables from the parent. When the process should be started in an
       * environment like in a terminal, the response of [fetchLoginShellEnvVariables] should be put into [ExecuteProcessOptions.env].
       *
       * All argument, all paths, should be valid for the remote machine. F.i., if the IDE runs on Windows, but IJent runs on Linux,
       * [ExecuteProcessOptions.workingDirectory] is the path on the Linux host. There's no automatic path mapping in this interface.
       */
      fun Builder(exe: String): Builder = ExecuteProcessBuilderImpl(exe)

      fun Builder(exe: String, arg1: String, vararg args: String): Builder = Builder(exe).args(listOf(arg1, *args))
    }
  }

  /**
   * Gets the same environment variables on the remote machine as the user would get if they run the shell.
   */
  suspend fun fetchLoginShellEnvVariables(): Map<String, String>

  interface ExecuteProcessError {
    val errno: Int
    val message: String
  }

  sealed interface PtyOrStdErrSettings

  /**
   * Runs a process with terminal (using `pty(7)`).
   * [echo] must be true in general and must be false when the user is asked for a password.
   *
   * Both `stderr` and `stdout` will be connected to this terminal, so `stderr` will be closed and merged with `stdout`
   * */
  data class Pty(val columns: Int, val rows: Int, val echo: Boolean) : PtyOrStdErrSettings

  /**
   * Do not use pty, but redirect `stderr` to `stdout` much like `redirectErrorStream` in JVM
   */
  data object RedirectStdErr : PtyOrStdErrSettings
}


/** Docs: [EelExecApi.executeProcessBuilder] */
@CheckReturnValue
suspend fun EelExecApi.execute(exe: String, setup: (EelExecApi.ExecuteProcessOptions.Builder).() -> Unit): EelResult<EelProcess, EelExecApi.ExecuteProcessError> {
  val builder = EelExecApi.ExecuteProcessOptions.Builder(exe).apply(setup).build()
  return execute(builder)
}

/** Docs: [EelExecApi.executeProcessBuilder] */
@CheckReturnValue
suspend fun EelExecApi.executeProcess(exe: String, vararg args: String): EelResult<EelProcess, EelExecApi.ExecuteProcessError> =
  execute(EelExecApi.ExecuteProcessOptions.Builder(exe).args(listOf(*args)).build())

fun EelExecApi.ExecuteProcessOptions.Builder.args(first: String, vararg other: String): EelExecApi.ExecuteProcessOptions.Builder =
  args(listOf(first, *other))


private data class ExecuteProcessBuilderImpl(
  override val exe: String,
  override var args: List<String> = listOf(),
  override var env: Map<String, String> = mapOf(),
  override var ptyOrStdErrSettings: PtyOrStdErrSettings? = null,
  override var workingDirectory: EelPath? = null,
) : EelExecApi.ExecuteProcessOptions, EelExecApi.ExecuteProcessOptions.Builder {

  override fun toString(): String =
    "GrpcExecuteProcessBuilder(" +
    "exe='$exe', " +
    "args=$args, " +
    "env=$env, " +
    "ptyOrStdErrSettings=$ptyOrStdErrSettings, " +
    "workingDirectory=$workingDirectory" +
    ")"

  override fun args(args: List<String>): ExecuteProcessBuilderImpl = apply {
    this.args = args
  }

  override fun env(env: Map<String, String>): ExecuteProcessBuilderImpl = apply {
    this.env = env
  }

  override fun ptyOrStdErrSettings(ptyOrStderrSettings: PtyOrStdErrSettings?): ExecuteProcessBuilderImpl = apply {
    this.ptyOrStdErrSettings = ptyOrStderrSettings
  }

  override fun workingDirectory(workingDirectory: EelPath?): ExecuteProcessBuilderImpl = apply {
    this.workingDirectory = workingDirectory
  }

  override fun build(): EelExecApi.ExecuteProcessOptions {
    return copy()
  }
}