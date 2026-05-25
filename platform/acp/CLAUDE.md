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

## Transport

This module deliberately stops at the process boundary: it owns the `EelProcess`
but not the ACP transport / `Protocol` on top of it.

The expected target is `com.agentclientprotocol.transport.StdioTransport` from
the ACP library, constructed via its Flow + suspend-writer constructor — the
non-blocking path that avoids dispatcher starvation under concurrent agents.
How `EelProcess.stdout` / `stdin` are adapted into a `Flow<String>` and a
`suspend (String) -> Unit` is the implementer's responsibility and is not part
of this module's surface.

The canonical in-tree adapter is `AcpTransportUtil.createEelStdioTransport`
in `plugins/llm/agents/acp/src/com/intellij/ml/llm/agents/acp/AcpTransportUtil.kt`
— see that file for the reference implementation (Flow over
`EelReceiveChannel.lines(Charsets.UTF_8)`, suspend writer over
`EelSendChannel.sendWholeBuffer`, `EelSendChannelException` translated to
`IOException`, eel channels closed in `onCompletion`).
