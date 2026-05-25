# ACP API (`intellij.platform.acp`)

Thin API surface that lets non-chat callers (code review, coding agents, etc.)
launch ACP agent processes and discover available agents without depending on
the heavier `intellij.ml.llm.agents.acp` module.

## What lives here

- `AcpProcessLauncher` — launches a single ACP agent process (no pooling, no
  chat-keying).
- `AcpServerProcessHandler`, `AcpProcessTermination`, `AcpProcessLaunchException`
  — process handle, termination outcome, and launch-time error type.
- `AcpAgentStartConfig` — resolved command/args/env for a process spawn.
- `AcpAgentId` and its subtypes (`AcpCustomAgentId`, `AcpRegistryAgentId`,
  `AcpRemoteAgentId`, `AcpPredefinedAgentId`) — type-safe agent id wrappers.
- `AcpAgentsCatalog` — read-only view of the process-launchable agents
  currently known to the IDE (local `acp.json` + external registry).

## Design decisions

Background and decisions for the launcher/catalog split are recorded in
YouTrack: <https://youtrack.jetbrains.com/issue/LLM-26920/Interface-for-running-a-process-for-the-acp-agent-for-Junie-Advanced-Mode#focus=Comments-27-13734207.0-0>.
