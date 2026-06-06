// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.acp

import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Launches a single ACP agent process. No pooling, no chat awareness — for chat-keyed reuse use
 * `AcpProcessHandlerService`.
 */
interface AcpProcessLauncher {
  /**
   * Resolves managed runtimes (npx/uvx, downloading them on demand), validates the command,
   * and spawns the process via EEL.
   *
   * Runtime resolution runs on the caller's coroutine. The spawned process and its stderr / exit
   * monitors are bound to [processLifetimeCoroutineScope]; cancelling that scope terminates the process. If the
   * caller's coroutine is cancelled mid-call the process state is undefined — it may or may not
   * have started — so cancelling [processLifetimeCoroutineScope] is the only reliable way to ensure it dies.
   * After a successful return, the caller's coroutine may die at any time without affecting the
   * running process.
   *
   * To react to process termination, launch an observer on
   * [processLifetimeCoroutineScope] (or a child of it) and `await()` on
   * [AcpServerProcessHandler.termination]. Using the same scope means the
   * observer is cleaned up automatically when the process lifetime ends.
   *
   * ```
   * val handler = launcher.startProcess(..., processLifetimeCoroutineScope = scope)
   * scope.launch {
   *   val outcome = handler.termination.await()
   *   if (outcome is AcpProcessTermination.Exited && outcome.exitCode != 0) {
   *     reportCrash(outcome.exitCode, outcome.stderr)
   *   }
   * }
   * ```
   *
   * @param agentName Human-readable display name of the agent, used purely for logging and error
   *   messages. Typically the registry id of the agent (`"claude-acp"`, `"gemini"`, `"goose"`,
   *   `"auggie"`) or the key from a local `acp.json` config.
   * @param config Resolved launch configuration: executable command, args, environment. For
   *   registry-installed agents this normally comes from `LocalAcpAgentConfig.resolveAgentStartConfig`;
   *   non-chat callers (Junie, self-review) may build it directly. Commands like `npx` / `uvx` are
   *   recognized and rewritten internally to use a managed runtime, so callers do not need to
   *   pre-resolve them.
   * @param projectDir Working directory the process is spawned in; agents resolve relative paths
   *   against it. Typically `project.guessProjectDir()?.toNioPath()`. Must point to a real path on
   *   the EEL environment selected by the project.
   * @param processLifetimeCoroutineScope Owns the spawned process and its monitoring coroutines. Pass a scope
   *   bound to whatever feature owns the agent — typically the chat session scope, or a one-shot
   *   feature scope (review run, Junie task). Must outlive the call itself.
   *
   * @throws AcpProcessLaunchException if runtime resolution, validation, or the process spawn fails.
   */
  suspend fun startProcess(
    agentName: String,
    config: AcpAgentStartConfig,
    projectDir: Path,
    processLifetimeCoroutineScope: CoroutineScope,
  ): AcpServerProcessHandler

  companion object {
    fun getInstance(project: Project): AcpProcessLauncher = project.service()
  }
}

@ApiStatus.Internal
interface AcpProcessLauncherProvider {
  companion object {
    val EP_NAME: ExtensionPointName<AcpProcessLauncherProvider> =
      ExtensionPointName.create("com.intellij.platform.acp.processLauncherProvider")
  }

  /**
   * Returns a launcher for [project] if this provider can handle ACP process launches there,
   * or `null` otherwise. Providers are queried in registration order.
   */
  fun createLauncher(project: Project): AcpProcessLauncher?
}
