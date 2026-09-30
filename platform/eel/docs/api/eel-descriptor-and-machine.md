# EelDescriptor, EelMachine, and EelApi

Read this page before you compare descriptors or convert a descriptor into a machine or an API.
This rule applies to API users and AI agents.

## Three Different Concepts

| Type | Purpose | I/O |
| --- | --- | --- |
| `EelDescriptor` | A lightweight identifier for access to an environment, including its path namespace. | You can obtain and compare descriptors without I/O. |
| `EelMachine` | The machine identity that the environment integration resolves from a descriptor. | Resolution can perform I/O. It can also use information that is already available. |
| `EelApi` | Access to a running environment through a specific descriptor. | Conversion can start or connect to the environment. Operations can perform I/O. |

Obtain a descriptor with `project.getEelDescriptor()` or `path.getEelDescriptor()`.
Prefer the project when you have an open project.
The descriptor does not prove that the environment exists, is reachable, or has a live connection.
It can remain available after the environment stops.
File operations on a routed NIO path can start a connection, even though obtaining its descriptor does not.

Use `descriptor.osFamily` for the OS family without a connection.
Use `api.platform` for the OS and architecture after you obtain an API.
`EelMachine` does not expose a `platform` property.
`descriptor.name` is a display label, not an identity key.

## Compare the Identity You Need

### Descriptor Identity

Use `first == second` when you need the same descriptor, such as the same path namespace.
Descriptor implementations define their equality.
Do not use `===` to compare separately obtained descriptors.

Different descriptors can identify access to the same machine.
For example, `\\wsl$\Ubuntu` and `\\wsl.localhost\Ubuntu` use different roots for the same WSL distribution.
The WSL descriptor includes the root in its equality check.
An unequal descriptor therefore does not prove that the machines differ.

Do not infer machine identity from a descriptor's name, class, root string, or OS family.
In particular, a non-local descriptor does not identify *your* remote machine.

### Machine Identity

Resolve both machines when the question is whether two descriptors address the same machine:

```kotlin
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.resolveEelMachine

suspend fun referToSameMachine(first: EelDescriptor, second: EelDescriptor): Boolean {
  val firstMachine = first.resolveEelMachine()
  val secondMachine = second.resolveEelMachine()
  return firstMachine == secondMachine
}
```

Use `==`, not `===`.
A resolver can return separate, equal machine objects.
Machine equality follows the integration's identity model.
It does not discover every possible alias for a physical host.

If I/O is not allowed, use `getResolvedEelMachine()` instead:

```kotlin
import com.intellij.platform.eel.provider.getResolvedEelMachine

val firstMachine = first.getResolvedEelMachine()
val secondMachine = second.getResolvedEelMachine()
val sameMachine: Boolean? = if (firstMachine != null && secondMachine != null) {
  firstMachine == secondMachine
}
else {
  null
}
```

Here, `null` means that the comparison is unknown.
It does not mean that the machines differ or that an environment is unavailable.
Never compare two nullable lookup results directly: two `null` results are not evidence of a shared machine.

For an existing machine, `machine.ownsDescriptor(descriptor)` checks ownership through that implementation.
`machine.ownsPath(path)` is the NIO convenience function.
An ownership check can reject a descriptor from another resolver.
Resolve both machines when you need to compare their identities across resolvers.

## Convert at the Correct Boundary

These extensions use the `com.intellij.platform.eel.provider` package:

| Call | Use |
| --- | --- |
| `descriptor.getResolvedEelMachine()` | Obtain a known machine without I/O. The result can be `null`. |
| `descriptor.resolveEelMachine()` | Resolve the machine. This suspends and can perform I/O or fail. |
| `project.getEelMachine()` | Obtain the machine associated with an initialized project. This does not perform resolution and can throw if initialization is missing. |
| `descriptor.toEelApi()` | Resolve the machine, then start or reuse access through this descriptor. This suspends and can perform I/O or fail. |
| `descriptor.toEelApiBlocking()` | Block the calling thread for the same conversion. Use only outside coroutines and outside the EDT. |

Call suspending conversions from a coroutine or a `suspend` function.
There is no `descriptor.machine` property in the current API.
Prefer `descriptor.toEelApi()` when you need operations, not just machine identity.
Do not obtain an API merely to compare machines.

If you already resolved the machine, retain the original descriptor:

```kotlin
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.resolveEelMachine

val descriptor = project.getEelDescriptor()
val machine = descriptor.resolveEelMachine()
val api = machine.toEelApi(descriptor)
```

The machine selects the connection.
The descriptor selects the API's path namespace, including the paths that the API returns.
`api.descriptor` identifies that namespace.
The caller must ensure that the descriptor addresses that machine.
A machine can reject a path namespace that it does not support.
Prefer `descriptor.toEelApi()` if you cannot establish that relationship.

Handle `EelUnavailableException` at the feature boundary and let the user restore access before retrying.
The local conversion returns `LocalEelMachine` or `localEel` without a remote connection.
Other integrations can also resolve a machine without I/O.
Neither successful resolution nor a cached machine proves that a later operation will succeed.

## Choose Cache Keys and Preserve Paths

- Use an `EelMachine` key for data that you can share across descriptors of that machine.
- Use an `EelDescriptor` key for data that depends on the path namespace.
- Do not cache an `EelApi` or descriptor-bound paths by machine alone and return them for another descriptor.
- Include other required keys, such as the project or user, when the data is not machine-wide.
- Equal machines do not make paths with different descriptors interchangeable. Keep the required descriptor when you obtain an API or build a path.

These excerpts show correct key selection in existing features.
They omit the surrounding declarations and imports.

### Machine Key: An SDK Table Bridge

The registry for SDK table bridges keeps one bridge per machine:

```kotlin
private val registry = ConcurrentHashMap<EelMachine, GlobalSdkTableBridge>()

override fun getTableBridge(eelMachine: EelMachine): GlobalSdkTableBridge {
  return registry.computeIfAbsent(eelMachine) {
    GlobalSdkBridgesLoader(eelMachine)
  }
}
```

A bridge processes SDK entries for its machine.
Different descriptors of that machine therefore select the same bridge.
The bridge retains the machine, not a caller's descriptor or an `EelApi`.
SDK paths still need the correct descriptor when a caller uses them.

### Descriptor Key: A Maven Repository Path

The Maven macro cache stores an absolute NIO path string for each descriptor:

```kotlin
private val repositoryByDescriptor = ConcurrentHashMap<EelDescriptor, String>()

return repositoryByDescriptor.computeIfAbsent(descriptor) {
  resolveDefaultLocalRepositoryForJpsMacros(it).toAbsolutePath().toString()
}
```

The returned string can include a descriptor-specific NIO root.
WSL aliases therefore need separate entries, even when they resolve to the same machine.
The helper in this excerpt is obsolete.
Copy the cache-key pattern, not the Maven helper.

## Check Descriptor Subinterfaces

Before you branch on a concrete descriptor class, check its subinterfaces and their KDocs.
They can expose information that the base interface does not provide.
Prefer the relevant capability or policy interface over a Docker, WSL, or SSH class check.
These interfaces do not change the distinction between descriptor identity and machine identity.

| Interface | Information |
| --- | --- |
| [`EelPathBoundDescriptor`](../../../eel-nioFs/src/com/intellij/platform/eel/EelPathBoundDescriptor.kt) | `rootPath` identifies the NIO root for this path namespace. It is not a machine identity. |
| [`EelDescriptorWithoutNativeFileChooserSupport`](../../src/com/intellij/platform/eel/EelDescriptor.kt) | Use the IDE file chooser instead of a native dialog for this environment. |
| [`EelDescriptorWithIsolatedWorkspace`](../../src/com/intellij/platform/eel/EelDescriptor.kt) | Keep the project's workspace state separate for the container environment. |
| [`EelDescriptorWithInteractiveDeployment`](../../src/com/intellij/platform/eel/EelDescriptor.kt) | Read `deploymentMayRequireUserInteraction`. The type check alone is insufficient because a delegating descriptor can change its answer. |

Most policy interfaces above are internal. Respect their API status and check the current declarations before use.
This list is not exhaustive.

For example, the file system guard uses this check in a callback:

```kotlin
(descriptor as? EelDescriptorWithInteractiveDeployment)?.deploymentMayRequireUserInteraction == true
```

The check reads the interface property instead of assuming a policy from a concrete SSH or Docker class.
The callback reads the current answer each time because a delegating descriptor can change its target.

## Source References

- [Descriptor and machine contracts](../../src/com/intellij/platform/eel/EelDescriptor.kt).
- [Machine lookup and conversion](../../src/com/intellij/platform/eel/provider/EelMachineResolver.kt).
- [Path lookup and ownership](../../../eel-nioFs/src/com/intellij/platform/eel/provider/EelPathDescriptor.kt).
- [Project access and blocking conversion](../../../eel-provider/src/com/intellij/platform/eel/provider/EelProvider.kt).
- [WSL descriptor equality](../../../platform-impl/eel/src/com/intellij/platform/ide/impl/wsl/WslEelProvider.kt).
- [EelPath and NIO Path](eel-path-and-nio-path.md).
