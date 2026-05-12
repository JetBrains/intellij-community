# Mast Remote Substrate Spike

## Current Conclusion

Gateway/Remote Development should be treated as a reference architecture, not as the Mast substrate.
The open-source Mast build is based on the IntelliJ IDEA Community product line and does not contain
the Gateway/RemoteDev product modules we would need to ship a branded Gateway-style client/backend.

The best path is to build a SAIL-native remote workspace layer on top of the IntelliJ Platform pieces
that are present in this source tree:

```text
Mac Mast UI
  -> IntelliJ Multi-Routing File System
  -> SAIL EEL descriptor for a project/container/repo
  -> IJent or SAIL-compatible helper deployed through SSH + incus exec
  -> remote filesystem, process execution, tunnels, Git, Maven, tests, terminal, agents
```

This is materially different from writing our own remote IDE. We should not start with a custom
`VirtualFileSystem` or a shadow project model. The platform already has a lower-level remote/local
environment abstraction designed for WSL, Docker-like containers, and non-local projects. Mast should
add a SAIL/Incus provider for that abstraction.

## Desired Product Shape

Mast should feel local on a MacBook Pro while all project authority lives in a SAIL Incus container:

```text
Mast.app on macOS
  -> open SAIL project "kubera"
  -> /home/dev/workspace/<repo> in the Incus container appears as an IntelliJ project
  -> Java indexing/navigation/refactoring operate through IntelliJ project APIs
  -> Git/Maven/tests/terminal/debug run in the container
  -> Codex/Claude/ACP sessions run in the same remote cwd and branch
  -> SAIL specs/kanban/snapshots/dispatch/logs/PR review are first-class tool windows
```

Incus is only the transport boundary. The user should not need an SSH daemon inside each container.
The connection should be:

```text
Mast -> SSH to bare-metal SAIL host -> incus exec <project-container> --user dev -- /bin/sh
```

From there the helper protocol owns filesystem, process, port, and agent operations.

## Platform Pieces Present In OSS

### Multi-Routing File System

The IntelliJ tree includes `MultiRoutingFileSystemProvider`, which replaces the default NIO file
system provider and delegates selected path prefixes to alternate file systems. Product build
constants enable it by default for normal IDE products through:

```text
-Djava.nio.file.spi.DefaultFileSystemProvider=com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider
```

This matters because the IDE, Maven import, Git integration, and a lot of Java platform code work
through `java.nio.file.Path`. A SAIL provider can make remote paths look like normal paths instead
of forcing every feature to learn a new URL scheme.

Relevant source:

- `platform/core-nio-fs/src/com/intellij/platform/core/nio/fs/MultiRoutingFileSystemProvider.java`
- `platform/eel-nioFs-impl/src/com/intellij/platform/eel/nioFs/impl/MultiRoutingFileSystemBackend.kt`
- `platform/ijent/buildConstants/src/com/intellij/platform/ijent/community/buildConstants/IjentBuildScriptsConstants.kt`

### EEL

EEL is the environment API for filesystem, process execution, tunnels, platform metadata, and user
info. It is explicitly shaped around local and remote environments.

The important interfaces are:

- `EelApi.fs`
- `EelApi.exec`
- `EelApi.tunnels`
- `EelExecApi.spawnProcess`
- `EelFileSystemApi.listDirectory`, `stat`, `openForReading`, and write operations

The local docs in this tree describe opening WSL and Docker projects directly through EEL as a
local-like experience instead of a heavy client/backend session:

- `platform/eel/docs/Opening_Projects_with_EelApi.md`
- `platform/eel/docs/EelApi_NIO_Integration.md`
- `platform/eel/src/com/intellij/platform/eel/EelApi.kt`
- `platform/eel/src/com/intellij/platform/eel/EelExecApi.kt`
- `platform/eel/src/com/intellij/platform/eel/fs/EelFileSystemApi.kt`

### IJent

IJent is the helper process API behind EEL for non-local machines. It provides a deployable process
that exposes filesystem, process, and tunnel services. The source tree contains the client-side
IJent APIs, NIO integration, and deployment strategies, including shell-based deployment.

The existing WSL strategy is the closest pattern for SAIL:

```text
WslIjentDeployingStrategy
  -> opens a non-interactive /bin/sh in the target
  -> uploads an IJent binary
  -> executes IJent
  -> exposes an EelApi for that environment
```

SAIL needs the same shape, except `createShellProcess()` runs:

```bash
ssh <sail-host> -- incus exec <container> \
  --user 1000 --group 1000 \
  --cwd /home/dev \
  -- /bin/sh
```

Relevant source:

- `platform/ijent/src/com/intellij/platform/ijent/IjentApi.kt`
- `platform/ijent/src/com/intellij/platform/ijent/spi/IjentDeployingOverShellProcessStrategy.kt`
- `platform/ijent/src/com/intellij/platform/ijent/spi/IjentControlledEnvironmentDeployingStrategy.kt`
- `platform/platform-impl/eel/src/com/intellij/platform/ide/impl/wsl/WslIjentDeployingStrategy.kt`
- `platform/platform-impl/eel/src/com/intellij/platform/ide/impl/wsl/ProductionWslIjentManager.kt`

Open issue: this source tree has the `IjentExecFileProvider` interface, but the default provider
throws `IjentMissingBinary`. We must prove whether the IJent binary can be built, downloaded, or
redistributed for Mast. If not, the fallback is a SAIL helper implementing the EEL-facing contracts,
but that is more work than adding a SAIL IJent deployer.

### Targets API

The Targets API already models executing processes on another machine/container with upload roots,
download roots, port bindings, command lines, and process creation. Maven and other run
configurations already know how to use targets.

Relevant source:

- `platform/execution/docs/target/targets-api-concepts.md`
- `platform/execution/src/com/intellij/execution/target/TargetEnvironment.kt`
- `platform/execution/src/com/intellij/execution/target/EelTargetEnvironmentRequest.kt`

Mast should use this for explicit run/test/debug tasks, while EEL/MRFS handles project opening and
file access.

### Git And Maven Already Understand EEL

This is the strongest sign that the architecture is viable.

Git has an `Eel` executable type and detection helper:

- `plugins/git4idea/src/git4idea/config/GitExecutable.kt`
- `plugins/git4idea/src/git4idea/config/GitEelExecutableDetectionHelper.kt`

The registry gate for non-local/container projects is:

```text
git.use.eel.for.container.projects
```

Maven has EEL remote process support for Maven server/import and Maven run execution:

- `plugins/maven/src/main/java/org/jetbrains/idea/maven/server/eel/EelMavenRemoteProcessSupportFactory.kt`
- `plugins/maven/src/main/java/org/jetbrains/idea/maven/server/eel/EelMavenServerRemoteProcessSupport.kt`
- `plugins/maven/src/main/java/org/jetbrains/idea/maven/execution/run/MavenCommandLineState.java`
- `plugins/maven/src/main/java/org/jetbrains/idea/maven/execution/run/MavenShCommandLineState.kt`

This means we are not inventing remote Git/Maven from scratch. We need SAIL projects to resolve to
an EEL descriptor and make sure product registry defaults are set correctly.

## Recommended Architecture

### 1. SAIL Project Connector

Add a Mast plugin/module that knows how to discover and open SAIL projects.

Responsibilities:

- Read local SAIL configuration or call a local `sail` helper/API.
- List projects, containers, repos, and remote root paths.
- Validate project names and repo ids against SAIL descriptors.
- Offer `Open SAIL Project...` from the welcome screen and File menu.
- Open a repo path through a SAIL multi-routing path rather than a local clone.

Example logical project path:

```text
/$sail.ij/<project>/<repo>/home/dev/workspace/<repo>
```

The exact prefix can change, but it must be:

- Stable across restarts.
- Unambiguous for project/container/repo.
- Non-spoofable by arbitrary user path text.
- Easy to map back to a SAIL project descriptor.

### 2. SAIL EEL Descriptor And Path Parser

Add a descriptor similar to `SshEelDescriptor` / `TcpEelDescriptor`.

Responsibilities:

- Represent one SAIL project container as an `EelDescriptor`.
- Include host id, container name, user, UID/GID, and allowed repo roots.
- Convert SAIL paths to EEL paths.
- Register a `MultiRoutingFileSystemBackend`.
- Register an environment initializer.
- Register an EEL machine resolver.

This is the point where IntelliJ starts seeing the project as an environment-backed path instead of
a normal local path.

### 3. SAIL IJent Deployment Strategy

Implement `SailIjentDeployingStrategy` by following the WSL deployer pattern.

Responsibilities:

- Start a non-interactive shell over `ssh host -- incus exec ... -- /bin/sh`.
- Upload and launch IJent in the container.
- Cache sessions per `(host, container, user)` with a `SailIjentManager`.
- Tear down sessions when the IDE/project closes.
- Treat container stop as expected process termination when appropriate.

Transport rules:

- Default to stdio over SSH + Incus, not public TCP.
- If TCP is required for performance, bind to container/host loopback only and create an SSH tunnel.
- Never expose helper ports on `0.0.0.0`.

### 4. Product Defaults

Mast should ship with the required remote-local substrate enabled.

Candidate defaults:

```text
-Djava.nio.file.spi.DefaultFileSystemProvider=com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider
-Djbr.java.io.use.nio=true
vfs.try.eel.for.content.loading=true
git.use.eel.for.container.projects=true
use.eel.file.watcher=true
```

The spike must verify exact current key names before shipping. Some local docs mention older or
draft registry names, and the source tree is the authority for Mast.

### 5. Remote Terminal

There are two viable paths:

1. Use EEL process spawning with PTY options and integrate with the existing terminal runner.
2. Start with a SAIL terminal tool window backed by a remote PTY channel, then merge into the normal
   terminal once stable.

The first path is better if the existing terminal APIs accept an EEL-backed process cleanly. The
second path is safer for a first vertical slice.

### 6. SAIL Specs And Agent Runtime

The agent/spec cockpit should not be separate from the remote project. It should bind to the same
SAIL EEL descriptor and project root.

Responsibilities:

- Display specs/kanban/logs from SAIL.
- Dispatch agents through the SAIL API or directly through EEL exec.
- Launch Codex/Claude with cwd set to the remote repo root.
- Stream agent stdout/stderr into Mast.
- Keep branch/snapshot state visible in the project UI.
- Treat spec markdown and agent output as untrusted content.

ACP should be implemented as a remote process/session adapter:

```text
Mast ACP UI
  -> SAIL project context
  -> EEL exec in /home/dev/workspace/<repo>
  -> codex/claude/gemini process in container
  -> stdio stream back to UI
```

## Alternative Architectures Considered

### Official Gateway

Best technical fit if we could ship it, but not the right OSS Mast substrate. It depends on product
modules and distribution terms that are not present in this source build.

Keep it as a UX reference and for manual comparison.

### Pure Custom VirtualFileSystem

Rejected as the first path. The IntelliJ Platform already has MRFS/EEL/IJent/NIO integration. A
custom `VirtualFileSystem` would bypass too much existing Java/Maven/Git work and force us to patch
subsystems one by one.

### Local Shadow Workspace

Useful fallback if EEL/IJent cannot be shipped or made stable. It would sync remote files into a
local cache and run commands remotely.

Pros:

- Fast indexing because IntelliJ sees normal local files.
- Smaller first prototype if remote FS blocks us.

Cons:

- More conflict handling.
- Sensitive client code lives on the Mac.
- Harder to guarantee Git/agent/filesystem state is one authoritative remote truth.

This should remain fallback, not the lead path.

### SSHFS/macFUSE Mount

Useful diagnostic tool, not a product substrate. It can quickly reveal whether local IntelliJ
features work with remote files, but performance, file watching, install friction, and consistency
are not good enough for our default workflow.

## Vertical Slice Plan

### Slice 1: Prove SAIL EEL Session

Goal: create an EEL session into one Incus project container.

Deliverables:

- `SailEelDescriptor`
- `SailIjentDeployingStrategy`
- `SailIjentManager`
- local action or test harness that runs `pwd`, `git --version`, `java -version`, and `mvn -version`
  through EEL in the container

Success criteria:

- No SSH daemon required inside the container.
- Commands run as `dev`.
- Session is cached and closed cleanly.
- No helper port is exposed remotely.

### Slice 2: Open Remote Project As Local-Like Project

Goal: open `/home/dev/workspace/<repo>` from the container as a Mast project.

Deliverables:

- SAIL MRFS backend.
- SAIL path parser/resolver.
- `Open SAIL Project...` action.
- Product registry/vmoption defaults.

Success criteria:

- Project tree loads.
- File open/read/write works.
- File watcher or refresh detects remote changes.
- Java files index without falling back to a local clone.

### Slice 3: Git And Maven

Goal: prove the Java company workflow.

Success criteria:

- Git root detected.
- Git status/diff/history works through `GitExecutable.Eel`.
- Maven import uses `EelMavenRemoteProcessSupport`.
- Maven test/run executes in the container.
- Remote JDK is discovered or configured without pointing to a Mac JDK.

### Slice 4: Terminal And Agents

Goal: connect the SAIL cockpit to the same remote root.

Success criteria:

- Terminal starts in the remote repo cwd.
- Codex/Claude starts in the remote repo cwd.
- Agent output streams back into Mast.
- Spec dispatch creates/uses the right branch in the right repo.
- Agent edits appear in the IDE through EEL refresh.

### Slice 5: Product Hardening

Goal: make it acceptable for daily engineering.

Work:

- Reconnect behavior.
- Session health checks.
- Index/cache persistence.
- Snapshot/branch guardrails.
- Token storage.
- SAIL project trust model.
- UI polish.
- Telemetry/log redaction.

## Security Requirements

- Validate project/container/repo/path from SAIL descriptors before executing anything.
- Do not accept arbitrary path prefixes as SAIL roots.
- Run remote commands as the project `dev` user, not host root.
- Keep helper ports loopback-only or avoid ports entirely.
- Do not log bearer tokens, SSH args containing secrets, webhook URLs, or agent prompts with secrets.
- Treat spec markdown and agent output as untrusted.
- Do not let a project choose its own helper binary path.
- Verify helper binary integrity if using IJent binaries.
- Maintain an allowlist of remote roots per project.
- Keep SAIL API authentication local-first and explicit for remote access.

## Open Questions

- Can Mast legally and technically ship the IJent binary required by `IjentExecFileProvider`?
- Is the IJent binary buildable from available public sources, or must it be obtained as an artifact?
- Which EEL registry flags are stable enough to make product defaults?
- Does Java indexing over SAIL MRFS meet performance expectations on large Maven projects?
- Can the existing terminal runner use EEL PTY directly, or do we need a SAIL terminal first?
- How much of debugger/test runner remote behavior works once the project descriptor is non-local?
- Do we need a SAIL-specific Target Environment type, or is `EelTargetEnvironmentRequest` enough?

## Immediate Recommendation

Create a new implementation spec for `mast-sail-eel-remote-workspaces`.

Do not invest further in Gateway enablement for Mast unless a separate legal/product decision says we
can depend on JetBrains proprietary Remote Development artifacts.

The next technical milestone is not branding or UI. It is a SAIL EEL session into one Incus
container, followed by opening a remote Maven project through the multi-routing filesystem.
