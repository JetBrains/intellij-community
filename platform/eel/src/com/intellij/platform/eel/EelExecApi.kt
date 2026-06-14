// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import com.intellij.platform.eel.EelExecApi.ExecuteProcessOptions
import com.intellij.platform.eel.channels.EelDelicateApi
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.channels.EelSendChannel
import com.intellij.platform.eel.path.EelPath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CheckReturnValue
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.util.Collections
import java.util.WeakHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Process execution inside the environment: starting a process and accessing its standard streams (stdin, stdout, stderr).
 *
 * Use this instead of `ProcessBuilder` when the process belongs to the project environment — it runs the process *there* (in WSL,
 * a container, …), not on the IDE host. Reach it via [EelApi.exec].
 *
 * [spawnProcess] is the entry point. It takes a builder (see [ExecuteProcessOptions]); configure it fluently and finish with `eelIt()`:
 * ```kotlin
 * val process = eel.exec.spawnProcess(exePath)
 *   .args("--version")
 *   .workingDirectory(projectRoot)
 *   .eelIt()
 * val exitCode = process.exitCode.await()
 * ```
 * The result is an [EelProcess] whose stdin/stdout/stderr and exit code are accessed through that handle.
 *
 * All arguments and paths must be valid *for the environment*, with no automatic host↔environment path mapping: if a value is a path
 * the spawned process will read, pass the environment-side form (e.g. an [EelPath] / `asEelPath()`), not the host path.
 *
 * Besides spawning, this API also exposes the environment's executable lookup ([findExeFilesInPath]) and its environment variables
 * ([environmentVariables]).
 */
@ApiStatus.Experimental
sealed interface EelExecApi {
  @get:ApiStatus.Experimental
  val descriptor: EelDescriptor

  /**
   * Executes the process, returning either an [EelProcess] or an error provided by the remote operating system.
   *
   * stdin, stdout and stderr of the process are always forwarded, if there are.
   *
   * The method may throw a RuntimeException only in critical cases like connection loss or a bug.
   *
   * All arguments and all paths should be valid for the remote machine. F.i., if the IDE runs on Windows, but IJent runs on Linux,
   * [ExecuteProcessOptions.workingDirectory] is the path on the Linux host. There's no automatic path mapping in this interface.
   *
   * See [ExecuteProcessOptions]
   */
  @Throws(ExecuteProcessException::class)
  @ThrowsChecked(ExecuteProcessException::class)
  @ApiStatus.Experimental
  suspend fun spawnProcess(@GeneratedBuilder generatedBuilder: ExecuteProcessOptions): EelProcess

  @CheckReturnValue
  @Deprecated("Use spawnProcess instead")
  @ApiStatus.Internal
  suspend fun execute(@GeneratedBuilder generatedBuilder: ExecuteProcessOptions): EelResult<EelProcess, ExecuteProcessError> {
    data class Ok<P : EelProcess>(override val value: P) : EelResult.Ok<P>
    data class Error(override val error: ExecuteProcessError) : EelResult.Error<ExecuteProcessError>
    data class ExecuteProcessErrorImpl(override val errno: Int, override val message: String) : ExecuteProcessError

    try {
      return Ok(spawnProcess(generatedBuilder))
    }
    catch (e: ExecuteProcessException) {
      return Error(ExecuteProcessErrorImpl(e.errno, e.message))
    }
  }

  /**
   * Options for [spawnProcess]: the executable plus how to run it (arguments, working directory, environment, terminal mode, lifetime).
   *
   * Normally built fluently via the [spawnProcess] builder rather than implemented directly.
   */
  @ApiStatus.Experimental
  interface ExecuteProcessOptions {
    /**
     * Command-line arguments passed to the process, not including the executable itself.
     */
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

    /**
     * Either an *absolute* path to the executable file or a binary name.
     *
     * When it's a binary name, the corresponginf executable is searched in the environment variable `PATH`.
     */
    @get:ApiStatus.Experimental
    val exe: String
  }

  @Suppress("FunctionName")
  @ApiStatus.Internal
  @ApiStatus.Obsolete
  suspend fun `_private useEnvironmentVariableDefaultInFetchLoginShellEnvVariables`(): Boolean = false

  /**
   * Use [environmentVariables] instead.
   *
   * This method is still not deprecated only because it has an automatically refreshable cache inside.
   * In contrast, [environmentVariables] only allows manually invalidating the cache.
   */
  @OptIn(EelDelicateApi::class)
  @ApiStatus.Experimental
  @ApiStatus.Obsolete
  suspend fun fetchLoginShellEnvVariables(): Map<String, String> {
    if (`_private useEnvironmentVariableDefaultInFetchLoginShellEnvVariables`()) {
      @Suppress("checkedExceptions")
      return environmentVariables().eelIt().await()
    }

    return when (this) {
      is EelExecPosixApi -> {
        if (this is LocalEelExecApi) {
          @Suppress("checkedExceptions")
          return environmentVariables().default().eelIt().await()
        }

        var now = 0L
        val cacheDuration = fetchLoginShellEnvVariablesCacheExpirationTime.inWholeNanoseconds

        // The previous implementation used the same timeout, and in the previous implementation it was chosen as a wild guess.
        val (expireAt, completedSuccessfullyLastTime) = cacheForObsoleteEnvVarExpireAt.compute(descriptor) { _, expireAtAndSucceeded ->
          now = System.nanoTime()
          when {
            expireAtAndSucceeded != null && expireAtAndSucceeded.first <= now -> expireAtAndSucceeded
            cacheDuration == Long.MAX_VALUE -> Long.MAX_VALUE to true
            else -> now + cacheDuration to true
          }
        }!!
        try {
          when {
            expireAt <= now -> {
              val result = environmentVariables().loginInteractive().onlyActual(true).eelIt().await()
              cacheForObsoleteEnvVarExpireAt.compute(descriptor) { _, expireAtAndSucceeded ->
                if (expireAtAndSucceeded == expireAt to true)
                  now + cacheDuration to true
                else
                  expireAtAndSucceeded
              }
              return result
            }
            completedSuccessfullyLastTime ->
              return environmentVariables().loginInteractive().onlyActual(false).eelIt().await()

            else -> Unit
          }
        }
        catch (err: Exception) {
          cacheForObsoleteEnvVarExpireAt.compute(descriptor) { _, expireAtAndSucceeded ->
            if (expireAtAndSucceeded == expireAt to true)
              expireAt to false
            else
              expireAtAndSucceeded
          }
          when (err) {
            is EnvironmentVariablesException -> Unit
            is TimeoutCancellationException -> currentCoroutineContext().ensureActive()
            else -> throw err
          }
        }
        @Suppress("checkedExceptions")
        environmentVariables().minimal().eelIt().await()
      }
      is EelExecWindowsApi -> @Suppress("checkedExceptions") environmentVariables().eelIt().await()
    }
  }

  /**
   * Gets the same environment variables on the remote machine as the user would get.
   *
   * *Notice:* use [EelExecApi.expandPathEnvVar] or [EelOsFamily.expandPathEnvVar] for `PATH`.
   *
   * See also [EelExecPosixApi.PosixEnvironmentVariablesOptions].
   */
  @ApiStatus.Experimental
  fun environmentVariables(@GeneratedBuilder opts: EnvironmentVariablesOptions): EnvironmentVariablesDeferred

  /**
   * Returns the path to the user's login shell on the Eel target.
   */
  @ApiStatus.Internal
  suspend fun getUserLoginShell(): EelPath

  /**
   * Spawns the user's login shell (resolved via [getUserLoginShell]) so that its full startup runs,
   * captures the resulting environment, and hands back a live PTY-attached interactive shell.
   */
  @ApiStatus.Internal
  @Throws(ExecuteProcessException::class)
  @ThrowsChecked(ExecuteProcessException::class)
  suspend fun spawnLoginShell(@GeneratedBuilder opts: LoginShellOptions): LoginShellHandle

  @ApiStatus.Internal
  interface LoginShellOptions {
    /**
     * Start the login shell with `-i` or equivalent so that the interactive profile is loaded.
     * */
    @get:ApiStatus.Internal
    val interactive: Boolean get() = true

    /**
     * PTY dimensions for the underlying shell session. If null, a default PTY is used.
     */
    @get:ApiStatus.Internal
    val pty: Pty? get() = null

    /**
     * Extra environment variables to pass to the outer shell process (e.g. `DISABLE_AUTO_UPDATE=true`
     * to silence oh-my-zsh's update prompt, or `LANG=en_US.UTF-8`). Merged into the inherited env
     * by the underlying [spawnProcess] — same semantics as [ExecuteProcessOptions.env].
     */
    @get:ApiStatus.Internal
    val env: Map<String, String> get() = mapOf()

    /**
     * Working directory of the outer shell process. Useful e.g. when the caller wants the shell to
     * start in a project root rather than `$HOME` — same semantics as [ExecuteProcessOptions.workingDirectory].
     */
    @get:ApiStatus.Internal
    val workingDirectory: EelPath? get() = null

    /**
     * Lifetime of the spawn. When canceled, the shell process is killed and
     * [LoginShellHandle.capturedEnv] completes exceptionally with [CancellationException].
     */
    @get:ApiStatus.Internal
    val scope: CoroutineScope? get() = null
  }

  /**
   * Result of [spawnLoginShell].
   */
  @ApiStatus.Internal
  interface LoginShellHandle {
    /**
     * Live shell process. Its `stdout` can be a **filtered** PTY stream - bytes between the two internal
     * sentinels (the env-capture window) are stripped from the consumer view; everything else (rcfile
     * output, post-capture interactive prompt) flows through untouched.
     *
     * **Caller must consume `stderr`.** This implementation does NOT drain stderr internally — terminal
     * widgets that surface stderr should attach a reader to `process.stderr` (e.g. forward into the
     * same widget, or into a side log). An unread stderr channel may block the shell once its kernel
     * pipe buffer fills.
     *
     * **Caller owns the lifecycle.** The process lives until `process.kill()` or until the spawn's
     * coroutine scope (see `LoginShellOptions.scope`) is canceled.
     */
    @get:ApiStatus.Internal
    val process: EelProcess
    @get:ApiStatus.Internal
    val capturedEnv: Deferred<List<EnvVar>>
  }

  @ApiStatus.Internal
  class EnvVar(
    val name: String,
    val value: String,
  )

  /**
   * Indicates on the failure during fetching environment variables.
   * As an API user, you can't gracefully handle this error. You can either ignore it or show the message to the user as a critical error.
   * The message text may be localized with the locale of the remote machine.
   */
  @ApiStatus.Experimental
  class EnvironmentVariablesException : EelError, IOException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
  }

  /**
   * This wrapper around [Deferred] exists only for pointing to the thrown error.
   */
  @ApiStatus.Experimental
  class EnvironmentVariablesDeferred @ApiStatus.Internal constructor(
    @ApiStatus.Experimental
    val deferred: Deferred<Map<String, String>>,
  ) {
    @ApiStatus.Experimental
    @ThrowsChecked(EnvironmentVariablesException::class)
    suspend fun await(): Map<String, String> = try {
      deferred.await()
    }
    catch (e: CancellationException) {
      currentCoroutineContext().ensureActive()
      throw RuntimeException("Environment variables fetching was cancelled", e)
    }
  }

  interface EnvironmentVariablesOptions {
    val mode: Mode get() = Mode.DEFAULT

    /**
     * The implementation MAY cache the environment variables by default because they rarely change in real life.
     * By setting this value to `true`, the cache will be refreshed, and the result will contain the freshest environment variables.
     *
     * Makes sense only for remote Eels (via IJent)
     * or with such [mode] that invoke a shell.
     * In other cases this option has no effect.
     */
    val onlyActual: Boolean get() = false

    enum class Mode {
      /**
       * Platform-defined fallback, never throws [EnvironmentVariablesException].
       *
       * * On remote POSIX Eel — like [LOGIN_NON_INTERACTIVE], but on error returns [MINIMAL] instead of throwing.
       * * On remote Windows Eel — registry view (like [LOGIN_NON_INTERACTIVE]).
       * * On local Windows/Linux — like [MINIMAL] (historical: the IDE rarely called the shell for env).
       * * On local macOS — like [LOGIN_NON_INTERACTIVE] + [MINIMAL], with values cached at start (historical).
       */
      DEFAULT,

      /**
       * Fastest path: inherited environment of the IJent process, no shell, no registry.
       * `PATH` is guaranteed; nothing else is.
       *
       * Never throws [EnvironmentVariablesException].
       */
      MINIMAL,

      /**
       * Fresh-logon snapshot.
       *
       * * On POSIX — non-interactive shell loading `~/.profile`, `~/.bashrc`, `~/.zshrc`, `/etc/profile` etc.
       *   May skip parts of `~/.bashrc` (e.g. `[ -z "$PS1" ] && return` early-exits).
       * * On Windows — registry view: `HKLM\...\Session Manager\Environment` merged with `HKCU\Environment`.
       *   No shell profile.
       *
       * **Notice:** MAY throw [EnvironmentVariablesException].
       */
      LOGIN_NON_INTERACTIVE,

      /**
       *  **Use with caution, avoid when possible.**
       *
       * Full interactive shell session.
       *
       * * On POSIX — interactive shell loading `~/.profile`, `~/.bashrc`, `~/.zshrc`, `/etc/profile` etc.
       *   Reads all environment variables unlike [LOGIN_NON_INTERACTIVE], but interactive shells aren't meant
       *   to run without a user. Real-world cases that broke users:
       *   * `ssh-add` in `~/.bashrc` waits for a passphrase — the shell hangs forever, IDE becomes unusable.
       *   * `~/.bashrc` starts `screen` or `tmux` — the shell hangs forever.
       *   * `~/.bashrc` starts `ssh-agent` — the OS gets polluted with unused agents.
       *   * `~/.bashrc` calls `curl` for weather/news/jokes — CPU usage grows, IDE slows down.
       * * On Windows — PowerShell with the user's `$PROFILE` loaded.
       *   Falls back to the registry view if PowerShell is unavailable or fails within the timeout.
       *
       * **Notice:** MAY throw [EnvironmentVariablesException].
       */
      @EelDelicateApi
      LOGIN_INTERACTIVE,

      /**
       * Like [LOGIN_INTERACTIVE], but uses the unified [spawnLoginShell] pipeline.
       *
       * **Notice:** MAY throw [EnvironmentVariablesException].
       */
      @ApiStatus.Internal
      LOGIN_INTERACTIVE_VIA_SHELL,
    }
  }

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
   */
  @ApiStatus.Internal
  interface ExternalCliEntrypoint {
    /**
     * Path to the callback script which can be passed to the tools like git.
     */
    val path: EelPath

    /**
     * Listens to the invocations of the script and lets [processor] to answer the cli requests.
     * Must be called exactly once, immediately after object creation.
     * Consider using [CoroutineStart.ATOMIC] or [CoroutineStart.UNDISPATCHED] for launching [consumeInvocations]
     * to guarantee that invocation actually happens.
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
     * Only the environment variables mentioned explicitly in [ExternalCliOptions.envVariablesToCapture] are guaranteed to be here.
     */
    val environment: Map<String, String>
    val pid: EelApi.Pid

    val stdin: EelReceiveChannel
    val stdout: EelSendChannel
    val stderr: EelSendChannel

    /**
     * Stop the callback script with exit code [exitCode].
     * Should be called exactly once.
     * After calling [exit], [stdin] [stdout] and [stderr] should not be used.
     */
    fun exit(exitCode: Int)
  }

  @ApiStatus.Internal
  sealed class ExternalCliLifecycle {
    /**
     * The entrypoint is created with unique name and will be deleted after the client cancels [ExternalCliEntrypoint.consumeInvocations].
     */
    object Default : ExternalCliLifecycle()

    /**
     * Serving of the external cli entrypoint created with this lifecycle will be suspended instead of stopping
     * when the client cancels [ExternalCliEntrypoint.consumeInvocations]. And subsequent [createExternalCli] calls
     * can internally reuse the same entrypoint (in case of coinciding call options), making subsequent [createExternalCli] calls faster.
     *
     * Cancelling the [scope] deletes the entrypoint and cancels running [ExternalCliEntrypoint.consumeInvocations] if there is one.
     */
    class Reusable(val scope: CoroutineScope) : ExternalCliLifecycle()
  }

  @ApiStatus.Internal
  interface ExternalCliOptions {
    /**
     * Prefix for an entrypoint executable file that will be created. Since the path to the entrypoint is passed to some command-line tool,
     * using a self-explaining prefix makes the command line more readable and easier to debug.
     */
    val filePrefix: String get() = ""

    /**
     * Create an entrypoint executable file with an exact name.
     */
    val exactName: String? get() = null

    val lifecycle: ExternalCliLifecycle get() = ExternalCliLifecycle.Default

    /**
     * Allowlist of environment variables mentioned here will be captured by the entrypoint and returned in [ExternalCliProcess.environment].
     * Capturing of other environment variables is not guaranteed.
     * If no environment variables are specified, no environment variables will be captured.
     */
    val envVariablesToCapture: List<String> get() = emptyList()
  }

  /**
   * It's obligatory to call [ExternalCliEntrypoint.consumeInvocations] on the resulting value.
   */
  @CheckReturnValue
  @ApiStatus.Internal
  suspend fun createExternalCli(@GeneratedBuilder options: ExternalCliOptions): ExternalCliEntrypoint

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
   * Do not use pty, but redirect `stderr` to [to]
   */
  @ApiStatus.Experimental
  class RedirectStdErr(val to: RedirectTo) : InteractionOptions

  @ApiStatus.Experimental
  enum class RedirectTo {
    /**
     * `/dev/null`, much like `DISCARD` in JVM
     */
    NULL,

    /**
     * `stdout` much like `redirectErrorStream` in JVM
     */
    STDOUT
  }
}

/**
 * [EelExecApi] for a POSIX environment. Spawns an [EelPosixProcess] and exposes POSIX environment-variable options.
 */
@ApiStatus.Experimental
interface EelExecPosixApi : EelExecApi {
  @ThrowsChecked(ExecuteProcessException::class)
  @ApiStatus.Experimental
  override suspend fun spawnProcess(@GeneratedBuilder generatedBuilder: ExecuteProcessOptions): EelPosixProcess

  @ApiStatus.Experimental
  override fun environmentVariables(
    @GeneratedBuilder(PosixEnvironmentVariablesOptions::class) opts: EelExecApi.EnvironmentVariablesOptions,
  ): EelExecApi.EnvironmentVariablesDeferred

  interface PosixEnvironmentVariablesOptions : EelExecApi.EnvironmentVariablesOptions

  @ApiStatus.Internal
  @ThrowsChecked(ExecuteProcessException::class)
  override suspend fun spawnLoginShell(
    @GeneratedBuilder opts: EelExecApi.LoginShellOptions,
  ): EelExecApi.LoginShellHandle
}

/**
 * [EelExecApi] for a Windows environment. Spawns an [EelWindowsProcess] and exposes Windows environment-variable options.
 */
@ApiStatus.Experimental
interface EelExecWindowsApi : EelExecApi {
  @ThrowsChecked(ExecuteProcessException::class)
  @ApiStatus.Experimental
  override suspend fun spawnProcess(@GeneratedBuilder generatedBuilder: ExecuteProcessOptions): EelWindowsProcess

  @ApiStatus.Experimental
  override fun environmentVariables(
    @GeneratedBuilder(WindowsEnvironmentVariablesOptions::class) opts: EelExecApi.EnvironmentVariablesOptions,
  ): EelExecApi.EnvironmentVariablesDeferred

  interface WindowsEnvironmentVariablesOptions : EelExecApi.EnvironmentVariablesOptions

  @ApiStatus.Internal
  @ThrowsChecked(ExecuteProcessException::class)
  override suspend fun spawnLoginShell(
    @GeneratedBuilder opts: EelExecApi.LoginShellOptions,
  ): EelExecApi.LoginShellHandle
}

/**
 * Returns the first executable named [exe] found in the environment's `PATH`, or `null` if none — like the Unix `which` command.
 *
 * Convenience over [findExeFilesInPath].
 */
@ApiStatus.Experimental
suspend fun EelExecApi.where(exe: String): EelPath? {
  return this.findExeFilesInPath(exe).firstOrNull()
}

/** Convenience builder over `spawnProcess` for an executable given as an [EelPath], pre-filling [ExecuteProcessOptions.args]. */
@ApiStatus.Experimental
fun EelExecApi.spawnProcess(exe: EelPath, vararg args: String): EelExecApiHelpers.SpawnProcess =
  spawnProcess(exe.toString()).args(*args)

/** Convenience builder over `spawnProcess` for an executable given by path or name, pre-filling [ExecuteProcessOptions.args]. */
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

/**
 * Prepare execution of [commands] in shell [getShell].
 */
@ApiStatus.Internal
suspend fun EelExecApi.execInShell(vararg commands: String): EelExecApiHelpers.SpawnProcess {
  val (shell, arg) = getShell()
  return spawnProcess(shell, arg, *commands)
}

/** Hopefully, it's a temporary workaround. */
@ApiStatus.Internal
interface LocalEelExecApi

/**
 * Value:
 * * Cache write time in nanoseconds
 * * `true` if the cache corresponds to a success record, false otherwise.
 */
@ApiStatus.Internal
@VisibleForTesting
val cacheForObsoleteEnvVarExpireAt: MutableMap<EelDescriptor, Pair<Long, Boolean>> = Collections.synchronizedMap(WeakHashMap())

// The previous implementation used the same timeout, and in the previous implementation it was chosen as a wild guess.
@ApiStatus.Internal
@VisibleForTesting
var fetchLoginShellEnvVariablesCacheExpirationTime: Duration = 10.seconds