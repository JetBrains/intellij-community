# Eel API Quick Reference

This page collects the most common Eel API calls on one screen. Read the [Eel API Tutorial](EelApi_Tutorial.md) for the full explanation.

## Core Concepts

### EelDescriptor vs EelMachine

**EelDescriptor** is a specific path-based access to an environment.

- It represents one way to access an environment. `\\wsl$\Ubuntu` and `\\wsl.localhost\Ubuntu` are two descriptors.
- It is a lightweight identifier. You can obtain it quickly.
- Different descriptors can point to the same physical machine.
- Use it when you work with specific paths.

**EelMachine** is the physical or logical host.

- It represents the actual machine: a container, a distribution, or a remote host.
- Multiple descriptors can resolve to the same machine.
- Use it as a cache key for shared resources such as connection pools.
- It holds the platform information: the OS family and the architecture.

```kotlin
// Two different descriptors.
val desc1 = Path.of("\\\\wsl$\\Ubuntu\\home").getEelDescriptor()
val desc2 = Path.of("\\\\wsl.localhost\\Ubuntu\\home").getEelDescriptor()

// They point to the same machine.
desc1.machine === desc2.machine  // true

// Use the machine for shared caching.
val cache: MutableMap<EelMachine, Data> = mutableMapOf()
cache[desc1.machine] = data  // Accessible through desc2.machine as well.
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

See [Path Conversion](EelApi_Path_Conversion.md) for the details.

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
