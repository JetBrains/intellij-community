# EelPath and nio Path

Eel has two path types: `com.intellij.platform.eel.path.EelPath` and `java.nio.file.Path`. This page states when to use each one, how they differ, and how to convert one into the other. [Two File System APIs](../overview/file-systems.md) explains the decision behind the two types.

## Which One to Use

Use `java.nio.file.Path` for I/O. `Files`, `EelFiles`, and `EelFileUtils` accept it. The platform, `VirtualFile`, and every existing API work with it. `Path.of("\\\\wsl$\\Ubuntu\\home\\user")` is a valid path to a file in WSL, and `Files.readString` on it reads the file in WSL.

Use `EelPath` when you need the path as the target machine sees it:

- an argument or an environment variable of a process started with `EelExecApi`,
- the working directory of such a process,
- a call to `EelFileSystemApi`, which is rare. See [Two File System APIs](../overview/file-systems.md).

`EelPath.toString()` returns the target path: `/home/user` for WSL, `C:\Users\user` for a Windows host.

## The Differences

| | `EelPath` | `java.nio.file.Path` |
| --- | --- | --- |
| Absolute or relative | Always absolute. | Absolute or relative. |
| A relative path | A `String`. See below. | A `Path` without a root. |
| The environment | `descriptor: EelDescriptor`. | `getEelDescriptor()` reads it from the file system. |
| The string form | The path on the target machine: `/home/user`. | The path on the IDE host: `\\wsl$\Ubuntu\home\user`. |
| I/O | None. Every method is a pure string operation. | `toRealPath()` and `register()` do I/O. |
| Creation | `EelPath.parse(string, descriptor)`. | `Path.of(string)`, `fs.getPath(string)`. |
| Identity | Immutable. Instances can be interned. | Depends on the file system. |

### EelPath Is Always Absolute

`EelPath.parse` accepts an absolute path only. It throws `EelPathException` for a relative one. A relative path is a plain `String`. `resolve(String)` and `getChild(String)` append it to an absolute path:

```kotlin
val home = EelPath.parse("/home/user", descriptor)
val project = home.resolve("projects/demo")   // /home/user/projects/demo
val file = project.getChild("build.gradle")   // one name, no separators allowed
EelPath.parse("projects/demo", descriptor)    // throws EelPathException
```

IJent never operates with a relative path. The platform does not know what the current working directory is. The working directory of the IJent process is a temporary directory, and no relative path makes sense there. A project directory is not an answer either: one IJent can serve several open projects.

A relative `java.nio.file.Path` exists, but `MultiRoutingFileSystem` resolves it against the working directory of the IDE process. Such a path never points into a remote environment. Keep a NIO path absolute when it can be remote.

### EelPath Has No I/O

Every `EelPath` method works on the string only: `parent`, `root`, `fileName`, `parts`, `normalize()`, `resolve()`, `getChild()`, `startsWith()`, `endsWith()`. `normalize()` removes `.` and `..` without a look at the file system. A symlink is never followed. To follow a symlink or to check that a file exists, convert the path to a `java.nio.file.Path` and call `Files`.

## Conversion

The functions live in `com.intellij.platform.eel.provider`. Both are pure string operations without I/O.

```kotlin
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor

// nio Path -> EelPath
val eelPath: EelPath = Path.of("\\\\wsl$\\Ubuntu\\home\\user").asEelPath()
eelPath.toString()      // "/home/user"
eelPath.descriptor      // the WSL descriptor for \\wsl$\Ubuntu

// EelPath -> nio Path
val nioPath: Path = eelPath.asNioPath()
nioPath.toString()      // "\\wsl$\Ubuntu\home\user"

// Only the descriptor, without the path.
val descriptor: EelDescriptor = Path.of("\\\\wsl$\\Ubuntu\\home\\user").getEelDescriptor()

// A local path converts too. The descriptor is LocalEelDescriptor.
Path.of("C:\\Windows").asEelPath().toString()   // "C:\Windows"
```

Rules of the conversion:

- `asEelPath()` requires an absolute NIO path. It throws `EelPathException` for a relative one.
- `asNioPath()` returns `Path.of(toString())` for `LocalEelDescriptor`. Every other descriptor needs a NIO root, so it must be an `EelPathBoundDescriptor`. The WSL and Docker descriptors are. `asNioPath()` throws `IllegalArgumentException` otherwise.
- The round trip keeps the root. A path under `\\wsl$\Ubuntu` converts back to `\\wsl$\Ubuntu`, not to `\\wsl.localhost\Ubuntu`. The two roots are two descriptors of one machine.
- `asEelPath()` on a path that is not routed by `MultiRoutingFileSystem` parses `toString()` as is. This covers a `Path` from an IJent NIO file system opened directly. See [NIO Routing Internals](../internal/nio-routing.md).

[Path Conversion](EelApi_Path_Conversion.md) shows more use cases: command-line arguments, environment variables, and display to the user.

## Path Operations and Different File Systems

A `java.nio.file.Path` belongs to one `FileSystem`. `Path.of(...)` returns a path of `MultiRoutingFileSystem`. A path from an IJent NIO file system opened directly belongs to that file system.

Pure string operations like `mrfsPath.resolve(ijentPath)` or `mrfsPath.startsWith(ijentPath)` require paths from the same file system. Convert through `EelPath` when you need to switch between the two file systems:

```kotlin
val samePathInMrfs: Path = ijentPath.asEelPath().asNioPath()
```

## Copy and Move Across File Systems

`Files.copy(source, target)` and `Files.move(source, target)` work across different environments and file systems. You can copy between local paths, WSL paths, Docker paths, and unwrapped `IjentNioPath` instances.

`MultiRoutingFileSystem` routes cross-filesystem operations to `EelPathTransfer.walkingTransfer`. This utility copies and moves single files and entire directory trees between different file systems. You can also call `EelPathTransfer.walkingTransfer` directly:

```kotlin
import com.intellij.platform.eel.provider.utils.EelPathTransfer

// Copy a directory tree between file systems.
EelPathTransfer.walkingTransfer(
  sourceRoot = Path.of("C:\\data"),
  targetRoot = Path.of("\\\\wsl$\\Ubuntu\\home\\user\\data"),
  removeSource = false,
  copyAttributes = true,
)
```
