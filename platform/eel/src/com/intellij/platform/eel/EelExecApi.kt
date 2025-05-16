// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import com.intellij.platform.eel.EelExecApi.ExecuteProcessOptions
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.channels.EelSendChannel
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.fs.EelFileSystemApi.DeleteError
import com.intellij.platform.eel.path.EelPath
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CheckReturnValue

/**
 * Methods related to process execution: start a process, collect stdin/stdout/stderr of the process, etc.
 */
sealed interface EelExecApi {

  val descriptor: EelDescriptor

  @Throws(ExecuteProcessException::class)
  @ThrowsChecked(ExecuteProcessException::class)
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
    val interactionOptions: InteractionOptions? get() = null

    @Deprecated("Switch to interactionOptions", replaceWith = ReplaceWith("interactionOptions"))
    val ptyOrStdErrSettings: PtyOrStdErrSettings? get() = interactionOptions

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

  /**
   * Represents a callback script which can be called from command-line tools like `git`.
   * The script passes its input data to the IDE and then passes back the answer.
   *
   * It's important to call [ExternalCliEntrypoint.delete] after the process which could call the script finishes
   * to avoid resource leak.
   */
  interface ExternalCliEntrypoint {
    /**
     * Path to the callback script which can be passed to the tools like git.
     */
    val path: EelPath

    /**
     * Every time the script is called, this channel gets a new item.
     * Use [delete] to stop listening.
     */
    val invocations: ReceiveChannel<ExternalCliProcess>

    /**
     * Stops listening and deletes the script file.
     */
    @CheckReturnValue
    suspend fun delete(): EelResult<Unit, DeleteError>

    /**
     * Helper method, todo move to extension function?
     * Listens to the invocations of external app lets [processor] to answer the cli requests.
     * Never exits normally, so should be canceled externally when not needed.
     */
    suspend fun listenAndDelete(processor: suspend (ExternalCliProcess) -> Int): Nothing {
      try {
        invocations.consumeEach { process ->
          val exitCode = processor(process)
          process.exit(exitCode)
        }
        error("Invocations channel shouldn't be closed other way than cancelling.")
      } finally {
        withContext(NonCancellable) {
          delete()
        }
      }
    }
  }

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
  suspend fun createExternalCli(options: ExternalCliOptions): EelResult<ExternalCliEntrypoint, EelFileSystemApi.CreateTemporaryEntryError>

  @Deprecated("Use spawnProcess instead")
  interface ExecuteProcessError : EelError {
    val errno: Int
    val message: String
  }

  @Deprecated("Switch to InteractionOptions", replaceWith = ReplaceWith("InteractionOptions"))
  sealed interface PtyOrStdErrSettings

  sealed interface InteractionOptions : PtyOrStdErrSettings

  /**
   * Runs a process with terminal (using `pty(7)`).
   * [echo] must be true in general and must be false when the user is asked for a password.
   *
   * Both `stderr` and `stdout` will be connected to this terminal, so `stderr` will be closed and merged with `stdout`
   * */
  data class Pty(val columns: Int, val rows: Int, val echo: Boolean) : InteractionOptions

  /**
   * Do not use pty, but redirect `stderr` to `stdout` much like `redirectErrorStream` in JVM
   */
  data object RedirectStdErr : InteractionOptions
}

interface EelExecPosixApi : EelExecApi {
  @ThrowsChecked(ExecuteProcessException::class)
  override suspend fun spawnProcess(@GeneratedBuilder generatedBuilder: ExecuteProcessOptions): EelPosixProcess
}

interface EelExecWindowsApi : EelExecApi {
  @ThrowsChecked(ExecuteProcessException::class)
  override suspend fun spawnProcess(@GeneratedBuilder generatedBuilder: ExecuteProcessOptions): EelWindowsProcess
}

suspend fun EelExecApi.where(exe: String): EelPath? {
  return this.findExeFilesInPath(exe).firstOrNull()
}

fun EelExecApi.spawnProcess(exe: String, vararg args: String): EelExecApiHelpers.SpawnProcess =
  spawnProcess(exe).args(*args)

fun EelExecPosixApi.spawnProcess(exe: String, vararg args: String): EelExecPosixApiHelpers.SpawnProcess =
  spawnProcess(exe).args(*args)

fun EelExecWindowsApi.spawnProcess(exe: String, vararg args: String): EelExecWindowsApiHelpers.SpawnProcess =
  spawnProcess(exe).args(*args)

/**
 * Path to a shell / command processor: `cmd.exe` on Windows and Bourne Shell (`sh`) on POSIX.
 * Second argument is the one you might provide to this shell to execute command and exit, i.e.: `cmd /C` or `sh -c`
 */
suspend fun EelExecApi.getShell(): Pair<EelPath, String> {
  val (shell, cmdArg) = when (this.descriptor.platform) {
    is EelPlatform.Windows -> {
      val envs = fetchLoginShellEnvVariables()
      Pair(envs["ComSpec"] ?: run {
        val winRoot = envs.getOrDefault("SystemRoot", "c:\\Windows")
        "$winRoot\\system32\\cmd.exe"
      }, "/C")
    }
    is EelPlatform.Posix -> {
      // TODO: use `confstr(3)` to get `PATH` with posix tools.
      val sh = findExeFilesInPath("sh").firstOrNull()?.toString() ?: "/bin/sh"
      Pair(sh, "-c")
    }
  }
  return Pair(EelPath.parse(shell, descriptor), cmdArg)
}