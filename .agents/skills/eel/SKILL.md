---
name: eel
description: EEL (Execution Environment Layer) API for local, WSL, and Docker environments. Use when working with process execution (ProcessBuilder, GeneralCommandLine), file paths (java.io.File, nio.Path across environments), OS/platform detection (SystemInfo), or environment variables in code that must support WSL or Docker.
---

# EEL (Execution Environment Layer) Skill

EEL is a unified API that makes IntelliJ code work transparently across local, WSL, and Docker environments. It replaces scattered WSL checks, `java.io.File`, `System.getenv`, `ProcessBuilder`, and `SystemInfo` with a single abstraction.

**Full documentation:** `community/platform/eel/docs/` (start with `README.md`)

## Key Rules

- **Use `nio.Path`, not `java.io.File`.** `nio.Path` integrates with EEL under the hood — NIO file operations are transparently routed through EEL file system providers, so `Files.readString(path)`, `Files.walk(path)`, etc. work correctly in remote environments (WSL, Docker) without any extra code. `java.io.File` has JBR compatibility patches but `nio.Path` is the proper API.
- **Use `EelApi.exec` for process execution, not `ProcessBuilder`.** This ensures processes run in the correct environment.
- **Use `EelPlatform` for OS detection, not `SystemInfo`.** `SystemInfo` reflects the IDE host machine, not the target environment.
- **`localEel` is a singleton that always represents the IDE host machine** (where the IDE process runs). Use it only when you specifically need the local environment. For project-relative work, get the descriptor from the project or path instead.

## Getting an EelDescriptor

```kotlin
// From a project (most common)
val descriptor = project.getEelDescriptor()

// From a path
val descriptor = Path.of("\\\\wsl.localhost\\Ubuntu\\home\\user").getEelDescriptor()

// Local environment singleton
val localApi: LocalEelApi = localEel
```

## Running Processes

```kotlin
val eelApi = descriptor.toEelApi()

val process = eelApi.exec.spawnProcess("git")
    .args("--version")
    .eelIt()

val exitCode = process.exitCode.await()
val output = process.stdout.readAllBytes().toString(Charsets.UTF_8)
```

## Path Conversion

```kotlin
// NIO Path → EelPath
val eelPath = nioPath.asEelPath()

// EelPath → NIO Path
val nioPath = eelPath.asNioPath()

// Use asEelPath() for environment-aware conversion (instead of EelPath.parse())
val eelPath = descriptor.asEelPath(project.basePath)
```

## Platform Detection

```kotlin
val eelApi = descriptor.toEelApi()
when {
    eelApi.platform.isPosix -> // Linux, macOS, FreeBSD
    eelApi.platform.isWindows -> // Windows
    eelApi.platform.isMac -> // macOS specifically
}
```

## Core Concepts

### EelDescriptor vs EelMachine

- **EelDescriptor**: lightweight, durable marker for a path-based access to an environment. Use for most operations.
- **EelMachine**: physical host instance. Use as cache key when managing shared resources (connection pools, etc.). Multiple descriptors can resolve to the same machine (e.g., `\\wsl$\Ubuntu` and `\\wsl.localhost\Ubuntu`).

### EelApi Subsystems

```kotlin
interface EelApi {
  val exec: EelExecApi           // Process execution
  val tunnels: EelTunnelsApi     // Network operations
  val archive: EelArchiveApi     // Archive/tar operations
  val platform: EelPlatformApi   // Platform detection
  val userInfo: EelUserInfoApi   // User information
}
```

## Best Practices

1. **Write environment-agnostic code** — avoid checking for `LocalEelDescriptor`; use EEL API uniformly
2. **Prefer `toEelApi()` over `toEelApiBlocking()`** — use the suspending version to avoid blocking threads
3. **Use `asEelPath()` helpers** — avoid manual WSL path conversion
4. **Cache by `EelMachine`** when managing shared resources across descriptors
5. **Close resources** — properly close tunnels, archive operations, and long-lived connections

## Documentation

- EEL Architecture: `community/platform/eel/docs/README.md`
- EEL Tutorial: `community/platform/eel/docs/EelApi_Tutorial.md`
- NIO Integration: `community/platform/eel/docs/EelApi_NIO_Integration.md`
- Path Conversion: `community/platform/eel/docs/EelApi_Path_Conversion.md`
- Real-World Examples: `community/platform/eel/docs/EelApi_Real_World_Examples.md`
