// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
   * stdin, stdout and stderr of the process are always forwarded, if there are.
   *
   * The method may throw a RuntimeException only in critical cases like connection loss or a bug.
   *
   * See [executeProcessBuilder]
   */
  @CheckReturnValue
  suspend fun execute(@GeneratedBuilder generatedBuilder: ExecuteProcessOptions): EelResult<EelProcess, ExecuteProcessError>

  interface ExecuteProcessOptions {
    val args: List<String> get() = listOf()

    /**
     * By default, environment is always inherited, which may be unwanted. [ExecuteProcessOptions.env] allows
     * to alter some environment variables, it doesn't clear the variables from the parent. When the process should be started in an
     * environment like in a terminal, the response of [fetchLoginShellEnvVariables] should be put into [ExecuteProcessOptions.env].
     */
    val env: Map<String, String> get() = mapOf()

    /**
     * When set pty, be sure to accept esc codes for a terminal you are emulating.
     * This terminal should also be set in `TERM` environment variable, so setting it in [env] worth doing.
     * If not set, `xterm` will be used as a most popular one.
     *
     * See `termcap(2)`, `terminfo(2)`, `ncurses(3X)` and ISBN `0937175226`.
     */
    val ptyOrStdErrSettings: PtyOrStdErrSettings? get() = null

    /**
     * All argument, all paths, should be valid for the remote machine. F.i., if the IDE runs on Windows, but IJent runs on Linux,
     * [ExecuteProcessOptions.workingDirectory] is the path on the Linux host. There's no automatic path mapping in this interface.
     */
    val workingDirectory: EelPath? get() = null

    // TODO: Use EelPath as soon as it will be merged
    //  We cannot do it currently until IJPL-163265 is implemented
    /**
     * An **absolute** path to the executable.
     * TODO Or do relative paths also work?
     *
     * All argument, all paths, should be valid for the remote machine. F.i., if the IDE runs on Windows, but IJent runs on Linux,
     * [ExecuteProcessOptions.workingDirectory] is the path on the Linux host. There's no automatic path mapping in this interface.
     */
    val exe: String
  }

  /**
   * Gets the same environment variables on the remote machine as the user would get if they run the shell.
   */
  suspend fun fetchLoginShellEnvVariables(): Map<String, String>

  /**
   * Finds executable files by name.
   * Directories for searching are iterated according to `PATH` env variable.
   * If [binaryName] is an absolute path, check that the file exists and is executable and returns its path without searching within
   * directories from `PATH`.
   *
   * Example:
   * ```kotlin
   * val path = eelApi.exec.findExeFilesInPath("git").firstOrNull()
   * if (path != null) {
   *     println("Git is located at: $path")
   * } else {
   *     println("Git executable not found.")
   * }
   * ```
   *
   * @param binaryName The name of the executable to search for, or an absolute path to the executable.
   * If it's not an absolute path, it's not supposed to contain path separators.
   * @return Full paths to all the executables found. Empty list if the executable is not found or an error occurs.
   * In most cases, only the first returned path is useful, but if there is more than one executable with the given name,
   * all of them are returned so that the preferable one can be chosen later.
   *
   */
  suspend fun findExeFilesInPath(binaryName: String): List<EelPath>

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

suspend fun EelExecApi.where(exe: String): EelPath? {
  return this.findExeFilesInPath(exe).firstOrNull()
}

fun EelExecApi.execute(exe: String, vararg args: String): EelExecApiHelpers.Execute =
  execute(exe).args(*args)