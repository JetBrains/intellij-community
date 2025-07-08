// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import com.intellij.platform.eel.EelExecApi.ExecuteProcessOptions
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.channels.EelSendChannel
import com.intellij.platform.eel.path.EelPath
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CheckReturnValue

/**
 * Methods related to process execution: start a process, collect stdin/stdout/stderr of the process, etc.
 */
@ApiStatus.Experimental
sealed interface EelExecApi {
  @get:ApiStatus.Experimental
  val descriptor: EelDescriptor

  @Throws(ExecuteProcessException::class)
  @ThrowsChecked(ExecuteProcessException::class)
  @ApiStatus.Experimental
  suspend fun spawnProcess(@GeneratedBuilder generatedBuilder: ExecuteProcessOptions): EelProcess

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
  @Deprecated("Use spawnProcess instead")
  @ApiStatus.Internal
  suspend fun execute(@GeneratedBuilder generatedBuilder: ExecuteProcessOptions): EelResult<EelProcess, ExecuteProcessError> {
    data class Ok<P : EelProcess>(override val value: P) : EelResult.Ok<P>
    data class Error(override val error: ExecuteProcessError) : EelResult.Error<ExecuteProcessError>
    data class ExecuteProcessErrorImpl(override val errno: Int, override val message: String) : ExecuteProcessError

    try {
      return Ok(spawnProcess(generatedBuilder))
    } catch (e: ExecuteProcessException) {
      return Error(ExecuteProcessErrorImpl(e.errno, e.message))
    }
  }

  @ApiStatus.Experimental
  interface ExecuteProcessOptions {
    @get:ApiStatus.Experimental
    val args: List<String> get() = listOf()

    /**
     * Scope this process is bound to. Once scope dies -- this process dies as well.
     */
    val scope: CoroutineScope? get() = null

    /**
     * By default, environment is always inherited, which may be unwanted. [ExecuteProcessOptions.env] allows
     * to alter some environment variables, it doesn't clear the variables from the parent. When the process should be started in an
     * environment like in a terminal, the response of [fetchLoginShellEnvVariables] should be put into [ExecuteProcessOptions.env].
     */
    @get:ApiStatus.Experimental
    val env: Map<String, String> get() = mapOf()

    /**
     * When set pty, be sure to accept esc codes for a terminal you are emulating.
     * This terminal should also be set in `TERM` environment variable, so setting it in [env] worth doing.
     * If not set, `xterm` will be used as a most popular one.
     *
     * See `termcap(2)`, `terminfo(2)`, `ncurses(3X)` and ISBN `0937175226`.
     */
    @get:ApiStatus.Experimental
    val interactionOptions: InteractionOptions? get() = null

    @Deprecated("Switch to interactionOptions", replaceWith = ReplaceWith("interactionOptions"))
    @get:ApiStatus.Internal
    val ptyOrStdErrSettings: PtyOrStdErrSettings? get() = interactionOptions

    /**
     * All argument, all paths, should be valid for the remote machine. F.i., if the IDE runs on Windows, but IJent runs on Linux,
     * [ExecuteProcessOptions.workingDirectory] is the path on the Linux host. There's no automatic path mapping in this interface.
     */
    @get:ApiStatus.Experimental
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
    @get:ApiStatus.Experimental
    val exe: String
  }

  /**
   * Gets the same environment variables on the remote machine as the user would get if they run the shell.
   */
  @ApiStatus.Experimental
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
  @ApiStatus.Experimental
  suspend fun findExeFilesInPath(binaryName: String): List<EelPath>

  /**
   * Represents a callback script which can be called from command-line tools like `git`.
   * The script passes its input data to the IDE and then passes back the answer.
   *
   * It's important to call [ExternalCliEntrypoint.delete] after the process which could call the script finishes
   * to avoid resource leak.
   */
  @ApiStatus.Internal
  interface ExternalCliEntrypoint {
    /**
     * Path to the callback script which can be passed to the tools like git.
     */
    val path: EelPath

    /**
     * Listens to the invocations of the script and lets [processor] to answer the cli requests.
     * Never exits normally, so should be canceled externally when not needed.
     */
    suspend fun consumeInvocations(processor: suspend (ExternalCliProcess) -> Int): Nothing
  }

  @ApiStatus.Internal
  interface ExternalCliProcess {
    val workingDir: EelPath
    val executableName: EelPath

    /**
     * Arguments passed to the script, `args[0]` is expected to be the name of the executable.
     */
    val args: List<String>

    /**
     * Only the environment variables which are mentioned explicitly in [ExecuteProcessOptions.env] are guaranteed to be here.
     */
    val environment: Map<String, String>
    val pid: EelApi.Pid

    val stdin: EelReceiveChannel
    val stdout: EelSendChannel
    val stderr: EelSendChannel

    /**
     * Stop the callback script with exit code [exitCode].
     * Should be called exactly once, after calling it [stdin] [stdout] and [stderr] should not be used.
     */
    fun exit(exitCode: Int)
  }

  @ApiStatus.Internal
  interface ExternalCliOptions {
    val filePrefix: String
    val envVariablesToCapture: List<String>
  }

  @ApiStatus.Internal
  // TODO remove when local implementation will implement the api properly
  interface LocalExternalCliOptions : ExternalCliOptions {
    val mainClass: Class<*>
    val useBatchFile: Boolean
  }

  // TODO Generate builder?
  @CheckReturnValue
  @ApiStatus.Internal
  suspend fun createExternalCli(options: ExternalCliOptions): ExternalCliEntrypoint

  @Deprecated("Use spawnProcess instead")
  @ApiStatus.Internal
  interface ExecuteProcessError : EelError {
    val errno: Int
    val message: String
  }

  @Deprecated("Switch to InteractionOptions", replaceWith = ReplaceWith("InteractionOptions"))
  @ApiStatus.Internal
  sealed interface PtyOrStdErrSettings

  @ApiStatus.Experimental
  sealed interface InteractionOptions : PtyOrStdErrSettings

  /**
   * Runs a process with terminal (using `pty(7)`).
   * [echo] must be true in general and must be false when the user is asked for a password.
   *
   * Both `stderr` and `stdout` will be connected to this terminal, so `stderr` will be closed and merged with `stdout`
   * */
  @ApiStatus.Experimental
  class Pty : InteractionOptions {
    val columns: Int
    val rows: Int

    @ApiStatus.Internal
    val echo: Boolean

    @ApiStatus.Experimental
    constructor(columns: Int, rows: Int) : this(columns, rows, true)

    @ApiStatus.Internal
    constructor(columns: Int, rows: Int, echo: Boolean) {
      this.columns = columns
      this.rows = rows
      this.echo = echo
    }
  }

  /**
   * Do not use pty, but redirect `stderr` to `stdout` much like `redirectErrorStream` in JVM
   */
  @ApiStatus.Experimental
  data object RedirectStdErr : InteractionOptions
}

@ApiStatus.Experimental
interface EelExecPosixApi : EelExecApi {
  @ThrowsChecked(ExecuteProcessException::class)
  @ApiStatus.Experimental
  override suspend fun spawnProcess(@GeneratedBuilder generatedBuilder: ExecuteProcessOptions): EelPosixProcess
}

@ApiStatus.Experimental
interface EelExecWindowsApi : EelExecApi {
  @ThrowsChecked(ExecuteProcessException::class)
  @ApiStatus.Experimental
  override suspend fun spawnProcess(@GeneratedBuilder generatedBuilder: ExecuteProcessOptions): EelWindowsProcess
}

@ApiStatus.Experimental
suspend fun EelExecApi.where(exe: String): EelPath? {
  return this.findExeFilesInPath(exe).firstOrNull()
}

@ApiStatus.Experimental
fun EelExecApi.spawnProcess(exe: String, vararg args: String): EelExecApiHelpers.SpawnProcess =
  spawnProcess(exe).args(*args)

@ApiStatus.Experimental
fun EelExecPosixApi.spawnProcess(exe: String, vararg args: String): EelExecPosixApiHelpers.SpawnProcess =
  spawnProcess(exe).args(*args)

@ApiStatus.Experimental
fun EelExecWindowsApi.spawnProcess(exe: String, vararg args: String): EelExecWindowsApiHelpers.SpawnProcess =
  spawnProcess(exe).args(*args)

/**
 * Path to a shell / command processor: `cmd.exe` on Windows and Bourne Shell (`sh`) on POSIX.
 * Second argument is the one you might provide to this shell to execute command and exit, i.e.: `cmd /C` or `sh -c`
 */
@ApiStatus.Internal
suspend fun EelExecApi.getShell(): Pair<EelPath, String> {
  val (shell, cmdArg) = when (this.descriptor.osFamily) {
    EelOsFamily.Windows -> {
      val envs = fetchLoginShellEnvVariables()
      Pair(envs["ComSpec"] ?: run {
        val winRoot = envs.getOrDefault("SystemRoot", "c:\\Windows")
        "$winRoot\\system32\\cmd.exe"
      }, "/C")
    }
    EelOsFamily.Posix -> {
      // TODO: use `confstr(3)` to get `PATH` with posix tools.
      val sh = findExeFilesInPath("sh").firstOrNull()?.toString() ?: "/bin/sh"
      Pair(sh, "-c")
    }
  }
  return Pair(EelPath.parse(shell, descriptor), cmdArg)
}