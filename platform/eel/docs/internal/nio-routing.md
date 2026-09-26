# NIO Routing Internals

This page is for Eel and IJent developers. It explains three things: how `MultiRoutingFileSystem` becomes the default NIO file system, how a backend mounts an IJent file system under a path prefix, and how to open an IJent NIO file system without the routing. [Two File System APIs](../overview/file-systems.md) gives the big picture.

## How MultiRoutingFileSystem Becomes the Default

The JDK reads the default file system provider from one system property. IntelliJ sets it in the VM options:

```
-Djava.nio.file.spi.DefaultFileSystemProvider=com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider
```

The JDK loads this class before the IDE class loaders exist. So the class and its module `intellij.platform.core.nio.fs` sit on the boot classpath. `IJENT_BOOT_CLASSPATH_MODULE` and `MULTI_ROUTING_FILE_SYSTEM_VMOPTIONS` in `IjentBuildScriptsConstants.kt` name them for the build scripts. `isMultiRoutingFileSystemEnabledForProduct()` lists the products that disable the option by default.

`MultiRoutingFileSystemVmOptionsSetter` writes the option into the user-level VM options file at the IDE start. A bundled VM option cannot be removed from the user level. So the setter has a second switch: `-Didea.force.default.filesystem=true` orders `MultiRoutingFileSystemProvider` to delegate everything to the local file system.

A test needs the same VM option. `@TestApplicationWithEel` fails with the name of the option when it is absent. In the unit test mode, the setter also adds `-Xbootclasspath/a:out/classes/production/intellij.platform.core.nio.fs`.

## The Layers of a Routed Path

`Path.of("\\\\wsl.localhost\\Ubuntu\\home\\user")` builds this chain:

| Layer | Class | Module | Role |
| --- | --- | --- | --- |
| 1 | `MultiRoutingFsPath` | `intellij.platform.core.nio.fs` | The path that the caller holds. It wraps the current delegate. `currentDelegate` unwraps it. |
| 2 | `IjentEphemeralRootAwarePath` | `intellij.platform.ijent.community.impl` | The bind mount. It holds the root `\\wsl.localhost\Ubuntu` and the IJent path. `toString()` and `toUri()` add the root back. |
| 3 | `AbsoluteIjentNioPath` | `intellij.platform.ijent.community.impl` | A path of `IjentNioFileSystem`. It holds an `EelPath` with the value `/home/user`. |
| 4 | `EelPath` and `EelFileSystemApi` | `intellij.platform.eel` | The call to IJent over gRPC. |

A local path has only layer 1. The delegate is a path of the JDK file system, `sun.nio.fs.UnixPath` or `sun.nio.fs.WindowsPath`.

Every operation of `MultiRoutingFileSystemProvider` unwraps the arguments, picks the delegate provider by the root of the first path, and wraps the result back. A two-path operation such as `Files.copy` asks the delegate providers whether they handle both paths. `RoutingAwareFileSystemProvider.canHandleRouting()` answers that. The IJent provider handles foreign paths and delegates cross-environment copy and move operations to `EelPathTransfer.walkingTransfer`.

## Backends

A backend mounts a file system under a prefix. It implements `MultiRoutingFileSystemBackend` from `intellij.platform.eel.nioFs.impl` and registers in `plugin.xml` as `com.intellij.multiRoutingFileSystemBackend`. `GlobalEelMrfsBackendProvider.install()` connects the extension point to `MultiRoutingFileSystem.setBackendProvider()`.

`compute(localFS, sanitizedPath)` runs on every path operation. It returns a `FileSystem` for a path under its prefix, or `null`. The path has `/` as the separator and no trailing slash. The contract is strict:

- Return fast. A path check must be a string prefix check. `MultiRoutingFileSystemBackendBenchmark` measures it.
- Do not load classes for a local path. `UrlClassLoader` can deadlock otherwise.
- Do not throw. `GlobalEelMrfsBackendProvider` logs an error and treats the path as local.
- Never return `localFS`. Return `null` instead.

`getCustomRoots()` adds the mounted roots to `FileSystem.getRootDirectories()`, so the file chooser lists them. `getCustomFileStores()` does the same for `FileSystem.getFileStores()`. Both run inside a read action at times, so they read a cache and do no I/O.

The existing backends:

| Backend | Module | Prefix |
| --- | --- | --- |
| `EelWslMrfsBackend` | `intellij.platform.ide.impl.wsl` | `\\wsl.localhost\<distro>`, `\\wsl$\<distro>` |
| `EelDockerMrfsBackend` | `intellij.clouds.docker`, ultimate | `\\docker.ij\<id>@<endpoint>`, `/$docker.ij/<id>@<endpoint>` |
| `SshEelMrfsBackend` | `intellij.platform.ijent.ssh`, ultimate | The SSH descriptor root |
| `TcpEelMrfsBackend` | `intellij.platform.eel.tcp` | The TCP descriptor root |

Every backend follows one pattern:

1. Get `IjentNioFileSystemProvider.getInstance()`. It is the JDK-installed provider for the `ijent` scheme.
2. Register an `IjentNioFileSystem` for the environment: `provider.newFileSystem(URI("ijent://wsl/Ubuntu"), IjentNioFileSystemProvider.newFileSystemMap(ijentFs))`. `ijentFs` is an `IjentFileSystemApi`. `ijentFailSafeFileSystemApi()` gives a lazy one that starts IJent on the first call. A second registration throws `FileSystemAlreadyExistsException`, and the backend ignores it.
3. Wrap it into `IjentEphemeralRootAwareFileSystemProvider(root = Path.of("\\\\wsl.localhost\\Ubuntu"), ...)` and return `getFileSystem(uri)` from it. This is the bind mount.
4. Cache the result per root.

The WSL backend still has the old wrapper `IjentWslNioFileSystemProvider` behind a flag. New code uses `IjentEphemeralRootAwareFileSystemProvider`.

`EelNioFsBackend` is a separate SPI in `intellij.platform.eel.nioFs`. `Path.getEelDescriptor()` calls it. The implementation `EelNioFsBackendImpl` unwraps `MultiRoutingFsPath` and reads `EelDescriptorOwner.eelDescriptor` from the delegate file system. The SPI exists so that `intellij.platform.eel.nioFs` does not depend on `intellij.platform.core.nio.fs`.

## An IJent NIO File System Without the Routing

`IjentNioFileSystemProvider` is a normal NIO provider with the scheme `ijent`. It works without `MultiRoutingFileSystem`. A path from it is an `IjentNioPath`, and its string form is the target path: `/home/user`.

```kotlin
import com.intellij.platform.ijent.community.impl.nio.IjentNioFileSystemProvider
import java.net.URI
import kotlin.io.path.isDirectory

val ijent: IjentApi = descriptor.toEelApi() as IjentApi
val provider = IjentNioFileSystemProvider.getInstance()

// Open. The URI must be unique. The authority and the path form the identifier.
val uri = URI("ijent://my-test/some-unique-id")
val fs = provider.newFileSystem(uri, IjentNioFileSystemProvider.newFileSystemMap(ijent.fs))

// Use. `fs.getPath()` returns an IjentNioPath. `Path.of()` never does.
val etc = fs.getPath("/etc")
etc.isDirectory()

// Find it again by the URI.
provider.getFileSystem(uri)

// Close. This removes the registration.
fs.close()
```

A Windows path from `provider.getPath(uri)` must contain a drive letter: `ijent://id/C:/Users`.

The production code registers these URIs:

| URI | Registered by |
| --- | --- |
| `ijent://wsl/<distribution id>` | `EelWslMrfsBackend` |
| `ijent://docker/<container id>` | `EelDockerMrfsBackend` |
| `ijent://ssh/<user@host:port>` | `SshEelMrfsBackend` |
| `ijent://tcp/<name>` | `TcpEelMrfsBackend` |

These URLs are not part of public API. The URLs may change at any time.

`AbstractIjentVerificationAction` in `intellij.platform.ijent.community.ui` is the one production use of this mode. It opens a file system with a random URI, checks that `/etc` is a directory, and closes it.

### In Tests

The IJent test framework in `platform/ijent/testFramework/`, ultimate, works in this mode:

- `EelFixtureOverridingService.registerMachine()` registers `ijent://wsl/<msId>`, `ijent://docker/<short id>`, or `ijent://<ksuid>` for a fixture and closes it with the lifetime. It also calls `IjentNioFileSystemProvider.maskFileSystems()`, so the test sees only its own file systems.
- `EelFileSystemApi.getOriginalNioFs()` in `IjentFileSystemTestUtil.kt` returns the `IjentNioFileSystem` that belongs to an `IjentFileSystemApi`. It calls the `@TestOnly` method `IjentNioFileSystemProvider.getNioFs(ijentFs)`. For the local file system it returns `FileSystems.getDefault()`.
- `fsTest { fsApiKind -> ... }` runs one test body twice: `FsApiKind.IjentFsApi` calls `EelFileSystemApi`, `FsApiKind.Nio` calls `Files` on the file system from `getOriginalNioFs()`.
- `execIjentFsTest` runs a local test body twice as well: once on `MultiRoutingFileSystem` and once on the JDK file system.
- `IjentWindowsFileSystemTest` and `IjentEphemeralRootAwarePathTest` create a private `IjentNioFileSystemProvider()` and register a file system under a fixed URI. A private provider is not installed in the JDK, so `Path.of()` and `Paths.get()` never reach it.

### Key Points

- `Path.of()` returns a `MultiRoutingFsPath`. An IJent file system opened directly returns an `IjentNioPath`.
- `Files.copy` and `Files.move` work across different file systems and environments via `EelPathTransfer.walkingTransfer`.
- `Path.getEelDescriptor()` works on an `IjentNioPath`, because `IjentNioFileSystem` is an `EelDescriptorOwner`. `asEelPath()` works too, and `asEelPath().asNioPath()` moves the path into `MultiRoutingFileSystem`.
