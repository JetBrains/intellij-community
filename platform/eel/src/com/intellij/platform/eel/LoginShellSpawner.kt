// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import com.intellij.platform.eel.EelExecApi.ExecuteProcessOptions
import com.intellij.platform.eel.EelExecApi.Pty
import com.intellij.platform.eel.path.EelPath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface LoginShellSpawner {
  @get:ApiStatus.Experimental
  val descriptor: EelDescriptor
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
}
