# LocalEelDescriptor and LocalEelMachine

## Overview

`LocalEelDescriptor` is a singleton that represents the environment where the IntelliJ IDE is installed — the machine where the IDE process runs.

While the Eel API provides a way to interact with various environments (like Docker containers or WSL distributions), `LocalEelDescriptor` specifically represents the native environment of the IDE itself.

## LocalEelDescriptor vs LocalEelMachine

Understanding the relationship between `LocalEelDescriptor` and `LocalEelMachine` is important:

**LocalEelDescriptor**:
- A singleton that identifies the local environment
- Implements the `EelDescriptor` interface
- Used to mark paths and projects that exist on the local machine
- Lightweight identifier

**LocalEelMachine**:
- A singleton that represents the physical local machine
- Implements the `EelMachine` interface
- Contains platform information (OS family, name)

They are related: `LocalEelDescriptor.machine === LocalEelMachine`

## Role in the EEL API

`LocalEelDescriptor` serves as the default environment when no other environment is applicable. When a path doesn't match any known remote environment pattern, it's assumed to be a local path and associated with `LocalEelDescriptor`.

**Examples:**
- `Path.of("C:\\Users\\...")` → `LocalEelDescriptor`
- `Path.of("\\\\wsl$\\Ubuntu\\...")` → `WslEelDescriptor`
- `Path.of("/docker-<id>/...")` → `DockerEelDescriptor`
- Paths without matching provider → `LocalEelDescriptor` (fallback)

## Usage Examples

### Checking if a File Belongs to the Local Environment

**Anti-pattern Warning**: Checking for `LocalEelDescriptor` creates machine-dependent code and should be avoided in most cases.

```kotlin
// ANTI-PATTERN: Avoid this if possible
if (path.getEelDescriptor() == LocalEelDescriptor) {
    ...
} else {
    ...
}

// PREFERRED: Use EEL API uniformly
val eelApi = path.getEelDescriptor().toEelApi()
// ... use eelApi operations that work everywhere
```

The EEL API is designed so that code works uniformly across all environments without needing to distinguish between local and remote.

### When LocalEelDescriptor Checks Are Acceptable

Checking for `LocalEelDescriptor` is justified only in these specific cases:

1. **Functionality tied to the IDE's host machine** — operations that must run where the IDE itself is installed (not where the project code executes).
2. **Tunneling and port forwarding** — remote environments require tunneling to forward ports between IDE and remote machine, while local environments use direct connections.

If you identify other legitimate use cases, please report them to the DevEnv team.

### Getting the Local EelApi

```kotlin
// Use localEel when you need the local API directly
val localApi: LocalEelApi = localEel

// For polymorphic code, use toEelApi()
fun doSomething(descriptor: EelDescriptor) {
    val api = descriptor.toEelApi()  // Works for any descriptor
}
```

## Platform Information

To get platform information about the local machine where the IDE runs:

```kotlin
// Get platform information via localEel
when {
    localEel.platform.isPosix -> println("IDE running on Unix-like OS")
    localEel.platform.isWindows -> println("IDE running on Windows")
    localEel.platform.isMac -> println("IDE running on macOS specifically")
}

// Get a human-readable name
println("Running on: ${LocalEelMachine.name}")
// Output: "Local: Windows 11" or "Local: macOS Sonoma"
```

## Conversion to EelApi

Converting `LocalEelDescriptor` to an `EelApi` instance is instant and doesn't involve any I/O:

```kotlin
// Suspending version
val api: EelApi = LocalEelDescriptor.toEelApi()

// Blocking version (also instant for local)
val api: EelApi = LocalEelDescriptor.toEelApiBlocking()

// Or use the singleton directly
val api: LocalEelApi = localEel
```

All three return the same singleton instance: `LocalEelDescriptor.toEelApi() === localEel`

## Best Practices

1. **Write environment-agnostic code**: Design APIs that work uniformly without checking for `LocalEelDescriptor`
2. **Use `localEel` directly**: When you know you need the local API, use `localEel` instead of converting from descriptor
