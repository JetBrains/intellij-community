# Exhaustive `when` for Sealed Types and Enums

This page is for everyone who writes code against Eel: API users, Eel and IJent developers, and AI agents.

The Eel API uses sealed classes, sealed interfaces, and enums in many places. Examples are `EelOsFamily`, `EelPlatform`, and `EelPlatform.Arch`. This page states how to check the value of such a type.

## The Rule

| Code | Status |
| --- | --- |
| An exhaustive `when` with a subject that lists every case and has no `else` branch | Correct |
| A helper next to the type, such as `val EelOsFamily.isWindows: Boolean` | Correct |
| `if (x == SomeEnum.SomeCase)`, `if (x is SomeSealed.SomeCase)`, or `x != SomeEnum.SomeCase` | Incorrect |
| A `when` with an `else` branch | Incorrect |

## Why

The compiler checks an exhaustive `when`. It stops the build when the `when` does not cover a case. An `if` check and an `else` branch disable this check.

The type hierarchy can change in these ways:

- Somebody adds a new subclass or a new enum entry.
- Somebody moves a subclass to a different parent in the hierarchy.
- Somebody changes the type of the checked value, for example from a sealed interface to an enum.

After such a change, an exhaustive `when` does not compile. The author of the change sees every place to update.

An `if` check or an `else` branch continues to compile. It silently goes into the wrong branch. An `is` check against a type that the value can no longer have is always `false`.

The result is a bug at runtime, not an error at compile time. Eel code runs on the local machine, in WSL, in Docker, and on SSH hosts. The wrong branch often runs only in one of these environments, or only on one OS. A test on the developer machine does not find it. Such a bug can take days to find.

## The Eel API Is Designed for an Exhaustive `when`

Many Eel interfaces are sealed. Each one has a sub-interface for POSIX and a sub-interface for Windows. Examples:

| Sealed interface | POSIX | Windows |
| --- | --- | --- |
| `EelExecApi` | `EelExecPosixApi` | `EelExecWindowsApi` |
| `EelProcessManagementApi` | `EelProcessManagementPosixApi` | `EelProcessManagementWindowsApi` |
| `EelTunnelsApi` | `EelTunnelsPosixApi` | `EelTunnelsWindowsApi` |
| `EelPlatform` | `EelPlatform.Posix` | `EelPlatform.Windows` |

A sub-interface can have methods that only its platform supports. For example, `EelProcessManagementPosixApi.terminate` sends `SIGTERM`. Windows has no such operation, so `EelProcessManagementWindowsApi` does not have this method.

`EelPosixApi` and `EelWindowsApi` narrow the type of each subsystem. For example, `EelPosixApi.exec` has the type `EelExecPosixApi`.

Use an exhaustive `when` to get the platform-specific methods. Do not cast:

```kotlin
when (val processManagement = eelApi.exec.processManagement) {
  is EelProcessManagementPosixApi -> processManagement.terminate(pid)
  is EelProcessManagementWindowsApi -> processManagement.kill(pid)
}
```

The compiler then shows each place where the code must handle the other platform.

## Examples

Correct. The `when` lists every case:

```kotlin
val scriptName = when (descriptor.osFamily) {
  EelOsFamily.Posix -> "mvn"
  EelOsFamily.Windows -> "mvn.cmd"
}
```

Correct. Only one case needs an action, but the `when` lists all cases:

```kotlin
when (descriptor.osFamily) {
  EelOsFamily.Posix -> setExecutableBit(path)
  EelOsFamily.Windows -> Unit
}
```

Correct. A sealed type:

```kotlin
val binaryName = when (platform) {
  is EelPlatform.Linux -> "tool-linux"
  is EelPlatform.Darwin -> "tool-darwin"
  is EelPlatform.FreeBSD -> "tool-freebsd"
  is EelPlatform.Windows -> "tool.exe"
}
```

Correct. A helper from the Eel API:

```kotlin
if (descriptor.osFamily.isWindows) {
  // ...
}
```

Incorrect. These checks compile after a change in the type hierarchy, but they give a wrong result:

```kotlin
if (descriptor.osFamily == EelOsFamily.Windows) { /* ... */ }

if (platform is EelPlatform.Windows) { /* ... */ }

val binaryName = when (platform) {
  is EelPlatform.Windows -> "tool.exe"
  else -> "tool-linux"  // A new Posix platform gets a Linux binary.
}
```

## Too Many Cases

Sometimes a `when` over a sealed class or a sealed interface has too many cases. Then look for a parent interface in the hierarchy. A sealed parent can replace all of its subclasses in one branch, and the `when` stays exhaustive.

This example from the IJent test framework lists every factory:

```kotlin
val isDocker = when (factory) {  // factory: EelFixture.Factory<*>
  is IjentLocalDockerFixture.DockerfileFactory,
  is IjentLocalDockerFixture.ImageFactory,
  is IjentTcpDockerFixture.Factory,
    -> true
  EelLocalFixture.Posix.Factory,
  EelLocalFixture.Windows.Factory,
  is LocalWslFixture.Factory,
  IjentPosixLocalFixture.Factory,
  IjentPosixTcpLocalFixture.Factory,
  is IjentWslFixture.Factory,
  IjentWindowsLocalFixture.Factory,
  IjentWindowsTcpLocalFixture.Factory,
  is SharedEelFactory<*, *>,
    -> false
}
```

The sealed parents make it shorter:

```kotlin
val isDocker = when (factory) {
  is IjentDockerFixture.Factory<*>,
    -> true
  is EelLocalFixture.Factory<*>,
  is IjentLocalFixture.Factory<*>,
  is IjentWslFixture.Factory,
  is SharedEelFactory<*, *>,
    -> false
}
```

The short version is still exhaustive. A new subclass of a listed parent goes into the branch of its parent. A new subclass with a new parent stops the build.

Use a parent only when all of its subclasses need the same branch. Do not use `else` to make a `when` shorter.

## Helpers

A helper such as `val EelOsFamily.isPosix: Boolean` or `val EelPlatform.isMac: Boolean` is correct. The helper is declared next to the type. The author who changes the type also sees the helper and updates it. The check stays in one place, not in many call sites.

Add a new helper to the file that declares the type, not to the call site. A private helper in a plugin file does not give this protection.

## Java

In Java, use a `switch` expression over the sealed type or the enum without a `default` branch. The compiler then checks the cases in the same way.

## For AI Agents

- Do not write `if (x == SomeEnum.SomeCase)`, `if (x is SomeSealed.SomeCase)`, or `else` in a `when` over a sealed type or an enum.
- Use an exhaustive `when` with a subject, or an existing helper next to the type.
- To get a platform-specific method, such as `EelProcessManagementPosixApi.terminate`, use an exhaustive `when` over the sealed interface. Do not cast.
- When a `when` has too many cases, use a sealed parent interface for a group of cases. Do not use `else`.
- When you see such an `if` check or `else` branch in code that you change, replace it with an exhaustive `when`.
