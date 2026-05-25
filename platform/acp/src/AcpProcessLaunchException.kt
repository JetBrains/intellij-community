// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.acp

import org.jetbrains.annotations.Nls

/**
 * Thrown by [AcpProcessLauncher.startProcess] (and its dependencies) when an ACP agent process
 * cannot be launched. Subclassed per failure case so callers can `catch` the specific one or
 * `is`-match for UI rendering / telemetry / recovery.
 *
 * The [message] of each subclass is an English diagnostic string for logs/telemetry. For
 * user-facing text, use [toUserMessage].
 */
sealed class AcpProcessLaunchException(message: String, cause: Throwable? = null) : Exception(message, cause) {

  /** Registry id or config key of the agent. Carried by every case for log/UI messages. */
  abstract val agentName: String

  /** Localized user-facing message. Safe to surface in chat error bubbles, balloons, etc. */
  abstract fun toUserMessage(): @Nls String

  /** The AI Assistant plugin (which ships the real launcher) is not installed. */
  class AiAssistantPluginMissing(override val agentName: String) : AcpProcessLaunchException(
    "Cannot launch ACP agent '$agentName': AI Assistant plugin is required"
  ) {
    override fun toUserMessage(): String = AcpPlatformBundle.message("error.launch.aia.plugin.missing", agentName)
  }

  /** Agent start config (command/args/env) could not be resolved before launch. */
  class ConfigResolutionFailed(override val agentName: String, cause: Throwable) : AcpProcessLaunchException(
    "Failed to resolve agent start config for '$agentName'", cause,
  ) {
    override fun toUserMessage(): String = AcpPlatformBundle.message("error.launch.config.resolution.failed", agentName)
  }

  /** Project directory could not be determined (`guessProjectDir()` returned null). */
  class ProjectDirectoryUnavailable(override val agentName: String) : AcpProcessLaunchException(
    "Cannot determine project directory for agent '$agentName'"
  ) {
    override fun toUserMessage(): String = AcpPlatformBundle.message("error.launch.project.dir.unavailable", agentName)
  }

  /** [AcpAgentStartConfig.command] is blank. */
  class CommandIsBlank(override val agentName: String) : AcpProcessLaunchException(
    "Cannot launch agent '$agentName': command is blank"
  ) {
    override fun toUserMessage(): String = AcpPlatformBundle.message("error.launch.command.blank", agentName)
  }

  /** [AcpAgentStartConfig.command] points to an absolute path that does not exist on disk. */
  class ExecutableNotFound(override val agentName: String, val command: String) : AcpProcessLaunchException(
    "Cannot launch agent '$agentName': executable not found: $command"
  ) {
    override fun toUserMessage(): String = AcpPlatformBundle.message("error.launch.executable.not.found", agentName, command)
  }

  /** Managed runtime (npx, uvx, …) could not be downloaded. */
  class RuntimeDownloadFailed(override val agentName: String, val runtimeName: String) : AcpProcessLaunchException(
    "Failed to download $runtimeName required by agent '$agentName'"
  ) {
    override fun toUserMessage(): String = AcpPlatformBundle.message("error.launch.runtime.download.failed", runtimeName, agentName)
  }

  /** EEL process spawn itself failed (wraps `ExecuteProcessException`). */
  class ProcessSpawnFailed(override val agentName: String, cause: Throwable) : AcpProcessLaunchException(
    "Failed to start process for agent '$agentName': ${cause.message}", cause,
  ) {
    override fun toUserMessage(): String = AcpPlatformBundle.message("error.launch.spawn.failed", agentName)
  }

  /** EEL path resolution failed (wraps `EelPathException`). */
  class InvalidPath(override val agentName: String, cause: Throwable) : AcpProcessLaunchException(
    "Failed to start process for agent '$agentName': ${cause.message}", cause,
  ) {
    override fun toUserMessage(): String = AcpPlatformBundle.message("error.launch.invalid.path", agentName)
  }

  /** Anything else — the underlying error is available via [cause]. */
  class Unknown(override val agentName: String, cause: Throwable) : AcpProcessLaunchException(
    "Failed to start process for agent '$agentName': ${cause.message}", cause,
  ) {
    override fun toUserMessage(): String = AcpPlatformBundle.message("error.launch.unknown", agentName)
  }
}
