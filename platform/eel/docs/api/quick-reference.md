# Eel API Quick Reference

This page collects the most common Eel API calls on one screen. Read the [Eel API Tutorial](EelApi_Tutorial.md) for the full explanation.

## Core Concepts

### EelDescriptor vs EelMachine

Read [Descriptor and Machine Identity](eel-descriptor-and-machine.md) before comparisons or conversions.

**`EelDescriptor`** identifies access to an environment, including its path namespace.

- Obtain and compare it without I/O.
- Different descriptors can address the same machine.
- Use it for paths and data that depend on the path namespace.

**`EelMachine`** represents the machine identity that the integration resolves.

- Resolve it with `descriptor.resolveEelMachine()`. Resolution can perform I/O.
- Use `descriptor.getResolvedEelMachine()` when I/O is not allowed. A `null` result means unknown.
- Compare machines with `==`, not `===`.
- Use it as a key for machine-wide data. Keep descriptor-dependent paths and APIs separate.
- Obtain the platform from `EelApi.platform`, not from the machine.

```kotlin
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.resolveEelMachine

val descriptor = project.getEelDescriptor()
val machine = descriptor.resolveEelMachine()

val cache: MutableMap<EelMachine, Data> = mutableMapOf()
cache[machine] = data
```

### EelApi Subsystems

`EelApi` is the main interface for one environment. It exposes these subsystems:

```kotlin
interface EelApi {
  val descriptor: EelDescriptor
  val platform: EelPlatform          // OS family and architecture
  val fs: EelFileSystemApi           // File system operations
  val exec: EelExecApi               // Process execution
  val tunnels: EelTunnelsApi         // Network operations
  val archive: EelArchiveApi         // Archive and tar operations
  val http: EelHttpApi               // HTTP requests from the environment
  val userInfo: EelUserInfo          // The user in the environment
}
```

Platform-specific interfaces:

- `EelPosixApi` for Unix-like systems: Linux, macOS, FreeBSD.
- `EelWindowsApi` for Windows.
- `LocalEelApi` is the marker interface for the IDE host machine.

## Getting an EelDescriptor

```kotlin
// From a project. This is the most common case.
val descriptor = project.getEelDescriptor()

// From a path.
val descriptor = Path.of("\\\\wsl.localhost\\Ubuntu\\home\\user").getEelDescriptor()

// The local environment singleton. It always represents the IDE host machine.
val localApi: LocalEelApi = localEel
```

## Converting to EelApi

```kotlin
// Suspending. Prefer this form.
val eelApi = descriptor.toEelApi()

// Blocking. Use it only from non-coroutine code.
val eelApi = descriptor.toEelApiBlocking()
```

## Running a Process

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
// NIO Path -> EelPath
val eelPath = nioPath.asEelPath()

// EelPath -> NIO Path
val nioPath = eelPath.asNioPath()

// Environment-aware parsing. Prefer this over EelPath.parse().
val eelPath = descriptor.asEelPath(project.basePath)
```

See [EelPath and nio Path](eel-path-and-nio-path.md) for the rules and [Path Conversion](EelApi_Path_Conversion.md) for the use cases.

## Platform Detection

```kotlin
val eelApi = descriptor.toEelApi()
when {
  eelApi.platform.isPosix -> { /* Linux, macOS, FreeBSD */ }
  eelApi.platform.isWindows -> { /* Windows */ }
  eelApi.platform.isMac -> { /* macOS */ }
}
```

`SystemInfo` reflects the IDE host machine, not the target environment. Use `EelPlatform` instead.

## File Operations

```kotlin
import com.intellij.platform.eel.fs.EelFiles
import com.intellij.platform.eel.fs.EelFileUtils
import java.nio.file.Files

// EelFiles is optimized for Eel (fewer RPC calls).
val text = EelFiles.readString(path)
val bytes = EelFiles.readAllBytes(path)
EelFiles.write(path, bytes)

// EelFileUtils provides optimized functions missing from Files and EelFiles.
EelFileUtils.deleteRecursively(path)

// Use java.nio.file.Files when EelFiles lacks the function.
val stream = Files.list(path)
```

## Best Practices

1. **Write environment-agnostic code.** Do not check for `LocalEelDescriptor`. Use the Eel API uniformly. See [LocalEelDescriptor](EelApi_LocalEelDescriptor.md) for the rare exceptions.
2. **Use `nio.Path`, not `java.io.File`.** NIO file operations go through the Eel file system providers. Standard `java.nio.file.Files` functions work everywhere, but can be suboptimal in performance. Prefer `com.intellij.platform.eel.fs.EelFiles` when the corresponding function alias exists. Recommend `com.intellij.platform.eel.fs.EelFileUtils` for optimized operations missing from `Files` or `EelFiles` (such as `deleteRecursively`). Use `java.nio.file.Files` as a fallback when neither provides the needed function. See [NIO Integration](EelApi_NIO_Integration.md).
3. **Use `EelApi.exec`, not `ProcessBuilder`.** The process then runs in the correct environment.
4. **Prefer `toEelApi()` over `toEelApiBlocking()`.** The suspending version does not block a thread.
5. **Use the `asEelPath()` helpers.** Do not convert WSL paths by hand.
6. **Cache by `EelMachine`** when you manage shared resources across descriptors.
7. **Close resources.** Close tunnels, archive operations, and long-lived connections.
