# EelApi Tutorial

## Introduction

The Eel API provides a unified interface for interacting with different execution environments, such as the local machine, Docker containers, WSL distributions, or remote SSH hosts. It abstracts away the differences between these environments, allowing you to write code that works consistently across them.

This tutorial will guide you through the core concepts of EelApi and show you how to use it effectively in your IntelliJ IDEA plugins.

## Core Concepts

### EelDescriptor vs EelMachine

Understanding the distinction between `EelDescriptor` and `EelMachine` is fundamental to working with EelApi:

**EelDescriptor** represents a specific path-based access to an environment:
- Lightweight identifier for an environment
- Multiple descriptors can point to the same physical machine
- Example: `\\wsl$\Ubuntu` and `\\wsl.localhost\Ubuntu` are different descriptors

**EelMachine** represents the physical or logical host:
- The actual machine (container, distribution, remote host)
- Multiple descriptors may resolve to the same machine
- Used for caching and resource pooling

Example:
```kotlin
// Two different descriptors
val descriptor1 = Path.of("\\\\wsl$\\Ubuntu\\home").getEelDescriptor()
val descriptor2 = Path.of("\\\\wsl.localhost\\Ubuntu\\home").getEelDescriptor()

// But they point to the same machine
descriptor1.machine === descriptor2.machine  // true

// Use machine for caching shared resources
val cache: MutableMap<EelMachine, SomeData> = mutableMapOf()
cache[descriptor1.machine] = data  // Accessible via descriptor2 as well
```

### EelDescriptor

An `EelDescriptor` is a lightweight marker for an environment where `EelApi` may exist. It provides basic information about the environment, such as the operating system type and a user-readable description.

Key characteristics of `EelDescriptor`:
- **Lightweight**: Much cheaper to obtain than a full `EelApi` instance
- **Durable**: Not affected by connection interruptions or container restarts
- **Convertible**: Can be converted to an `EelApi` instance when needed

Example of obtaining an `EelDescriptor`:

```kotlin
// From a path
val descriptor = Path.of("\\\\wsl.localhost\\Ubuntu\\home\\user\\projects").getEelDescriptor()

// From a project
val descriptor = project.getEelDescriptor()
```

### EelApi

The `EelApi` interface is the main entry point for interacting with an environment. It provides access to various subsystems:

- **Execution** (`exec`): For running processes
- **Tunnels** (`tunnels`): For network communication
- **Archive** (`archive`): For working with archives
- **Platform Information** (`platform`): For getting OS and architecture details
- **User Information** (`userInfo`): For getting information about the current user

Example of obtaining an `EelApi` instance:

```kotlin
// From an EelDescriptor
val eelApi = eelDescriptor.toEelApi()

// Or synchronously (blocks the current thread)
val eelApi = eelDescriptor.toEelApiBlocking()
```

### Platform-Specific APIs

EelApi provides platform-specific interfaces for Posix and Windows systems:

- `EelPosixApi`: For Unix-like systems (Linux, macOS, FreeBSD)
- `EelWindowsApi`: For Windows systems

These interfaces provide access to platform-specific features and ensure type safety when working with different platforms.

## Process Execution

The `EelExecApi` interface provides methods for running processes:

### Running a Process

The process execution API is consistent across platforms, with the main difference being the executable path and arguments:

```kotlin
// Common pattern for running a process on any platform
val process = when {
    eelApi.platform.isPosix -> {
        // For Posix systems (Linux, macOS, FreeBSD)
        (eelApi as EelPosixApi).exec.spawnProcess("/bin/ls")
            .args("-la")
            .workingDirectory(EelPath.parse("/home/user", eelApi.descriptor))
            .eelIt()
    }
    eelApi.platform.isWindows -> {
        // For Windows systems
        (eelApi as EelWindowsApi).exec.spawnProcess("cmd.exe")
            .args("/c", "dir")
            .workingDirectory(EelPath.parse("C:\\Users", eelApi.descriptor))
            .eelIt()
    }
    else -> throw UnsupportedOperationException("Unsupported platform")
}

// Wait for process to complete
val exitCode = process.exitCode.await()
println("Process exited with code: $exitCode")

// Read output
val output = process.stdout.readAllBytes().toString(Charsets.UTF_8)
println("Output: $output")

// Read error output
val errorOutput = process.stderr.readAllBytes().toString(Charsets.UTF_8)
println("Error output: $errorOutput")
```

You can also use the platform-agnostic helper functions when the command is available on all platforms:

```kotlin
// Platform-agnostic way to run a command that exists on all platforms (like 'git')
val gitProcess = eelApi.exec.spawnProcess("git")
    .args("--version")
    .eelIt()

val gitVersion = gitProcess.stdout.readAllBytes().toString(Charsets.UTF_8)
println("Git version: $gitVersion")
```

### Converting to Java Process

If you need to use the process with APIs that expect `java.lang.Process`, you can convert it:

```kotlin
val eelProcess = eelApi.exec.spawnProcess("git").args("status").eelIt()

// Convert to Java Process for compatibility
val javaProcess: Process = eelProcess.convertToJavaProcess()
```

### Using GeneralCommandLine

You can also run processes using `GeneralCommandLine`, which is a common way to execute external processes in IntelliJ IDEA:

```kotlin
// Create a GeneralCommandLine
val commandLine = GeneralCommandLine("mvn", "clean", "install")
    .withWorkDirectory("\\\\wsl$\\Ubuntu\\path\\to\\project")

// Execute the process
val process = commandLine.createProcess()
```

When using `GeneralCommandLine` with EelApi, the execution environment (local, WSL, Docker, etc.) is determined by:
- The working directory path (`withWorkDirectory`)
- The executable path (`withExePath`)

It's important to note that arguments and environment variables must be separately converted to remote paths using `asEelPath`:

```kotlin
// Convert paths in arguments to remote paths
val projectFile = Path.of("\\\\wsl$\\Ubuntu\\path\\to\\project\\pom.xml").asEelPath().toString()
commandLine.addParameter("-f")
commandLine.addParameter(projectFile)

// Convert paths in environment variables to remote paths
val javaHome = Path.of(jdkPath).asEelPath().toString()
commandLine.withEnvironment("JAVA_HOME", javaHome)

// Helper function for converting paths
fun String.asTargetPathString(): String = Path.of(this).asEelPath().toString()
commandLine.addParameter("-s")
commandLine.addParameter(settingsFile.asTargetPathString())
```

This ensures that paths are correctly formatted for the target environment, whether it's local, WSL, Docker, or another remote environment.

### Finding Executables

```kotlin
// Find all instances of a binary in PATH
val exeFiles = eelApi.exec.findExeFilesInPath("git")
for (exeFile in exeFiles) {
    println("Found git at: $exeFile")
}

// Or get just the first one
val gitPath = eelApi.exec.where("git")
if (gitPath != null) {
    println("Git found at: $gitPath")
}
```

### Process Control

```kotlin
// Kill the process (SIGKILL on Unix, TerminateProcess on Windows)
process.kill()

// Interrupt the process (SIGINT on Unix, CTRL+C on Windows)
process.interrupt()

// Terminate gracefully (SIGTERM on Unix, only available on Posix)
if (process is EelPosixProcess) {
    process.terminate()
}

// Resize PTY if the process is running with a terminal
try {
    process.resizePty(columns = 120, rows = 40)
} catch (e: EelProcess.ResizePtyError) {
    // Handle error (process exited, no PTY, etc.)
}
```

## Network Operations

The `EelTunnelsApi` interface provides methods for network communication:

### Connecting to a Remote Port

**Recommended approach**: Use the `withConnectionToRemotePort` helper that automatically handles connection cleanup:

```kotlin
// Preferred: automatic connection management
eelApi.tunnels.withConnectionToRemotePort(
    host = "localhost",
    port = 8080u,
    errorHandler = { error ->
        println("Connection failed: ${error.message}")
    }
) { connection ->
    // Configure socket options
    connection.configureSocket {
        setNoDelay(true)
    }
    
    // Use channels
    val (sendChannel, receiveChannel) = connection
    
    // Send data
    sendChannel.send("Hello, World!".toByteArray())
    
    // Receive data
    val response = receiveChannel.receive(1024)
    println("Received: ${response.toString(Charsets.UTF_8)}")
    
    // Connection is automatically closed when block exits
}
```

**Manual approach**: For more control, manage the connection yourself:

```kotlin
// Create a host address
val hostAddress = EelTunnelsApi.HostAddress.Builder(8080u)
    .hostname("localhost")
    .preferIPv4()
    .connectionTimeout(10.seconds)
    .build()

// Connect to the remote port
val connection = eelApi.tunnels.getConnectionToRemotePort()
    .hostAddress(hostAddress)
    .eelIt()

try {
    // Configure socket options
    connection.configureSocket {
        setNoDelay(true)
    }
    
    // Get channels
    val sendChannel = connection.sendChannel
    val receiveChannel = connection.receiveChannel
    
    // Send data
    sendChannel.send("Hello, World!".toByteArray())
    
    // Receive data
    val response = receiveChannel.receive(1024)
    println("Received: ${response.toString(Charsets.UTF_8)}")
} finally {
    // Always close the connection
    connection.close()
}
```

### Working with Unix Sockets

```kotlin
// Listen on a Unix socket with automatic path generation
val (socketPath, sendChannel, receiveChannel) = eelApi.tunnels.listenOnUnixSocket()
    .prefix("myapp-")
    .suffix(".sock")
    .eelIt()

println("Listening on: $socketPath")

// Handle the connection
// ...

// Or listen on a specific path
val fixedPath = EelPath.parse("/tmp/myapp.sock", eelApi.descriptor)
val (_, tx, rx) = eelApi.tunnels.listenOnUnixSocket(fixedPath)
```

## Platform-Specific Features

While the EelApi provides a unified interface for most operations, there are cases where you need to use platform-specific features. The API is designed to make this easy by providing platform-specific interfaces.

### Getting System Information

This example shows how to get system information on different platforms:

```kotlin
// Common pattern for platform-specific operations
val systemInfo = when {
    eelApi.platform.isPosix -> {
        // For Posix systems (Linux, macOS, FreeBSD)
        val posixApi = eelApi as EelPosixApi
        val process = posixApi.exec.spawnProcess("/bin/sh")
            .args("-c", "uname -a")
            .eelIt()
        
        process.stdout.readAllBytes().toString(Charsets.UTF_8).also {
            process.exitCode.await()
        }
    }
    eelApi.platform.isWindows -> {
        // For Windows systems
        val windowsApi = eelApi as EelWindowsApi
        val process = windowsApi.exec.spawnProcess("cmd.exe")
            .args("/c", "systeminfo")
            .eelIt()
        
        process.stdout.readAllBytes().toString(Charsets.UTF_8).also {
            process.exitCode.await()
        }
    }
    else -> "Unknown platform"
}

println("System info: $systemInfo")
```

### Platform-Specific API Features

Some features are only available on specific platforms:

```kotlin
when {
    eelApi.platform.isPosix -> {
        val posixApi = eelApi as EelPosixApi
        
        // Get Posix-specific user information
        val userInfo = posixApi.userInfo
        println("User ID: ${userInfo.uid}")
        println("Group ID: ${userInfo.gid}")
        
        // Use Posix-specific process features
        val process = posixApi.exec.spawnProcess("/bin/bash")
            .args("-c", "echo $HOME")
            .eelIt()
        // ...
    }
    eelApi.platform.isWindows -> {
        val windowsApi = eelApi as EelWindowsApi
        
        // Get Windows-specific user information
        val userInfo = windowsApi.userInfo
        println("User SID: ${userInfo.sid}")
        
        // Use Windows-specific process features
        val process = windowsApi.exec.spawnProcess("powershell.exe")
            .args("-Command", "echo $env:USERPROFILE")
            .eelIt()
        // ...
    }
}
```

## Integration with Docker

EelApi automatically detects and works with Docker containers when you use Docker paths:

```kotlin
// Docker integration is handled automatically through paths
// The path format varies by Docker runtime and setup
val dockerPath = Path.of("/docker-<containerId>/app/data")
val dockerDescriptor = dockerPath.getEelDescriptor()

// Or get descriptor from a project in Docker
val descriptor = project.getEelDescriptor()

// Convert to EelApi
val dockerApi = descriptor.toEelApi()

// Use the API to interact with the container
val process = (dockerApi as EelPosixApi).exec.spawnProcess("/bin/ls")
    .args("-la", "/app")
    .eelIt()

val output = process.stdout.readAllBytes().toString(Charsets.UTF_8)
println("Container contents: $output")
```

**Note**: See [Opening Projects with EelApi](Opening_Projects_with_EelApi.md) for details on opening Docker projects.

## Integration with WSL

EelApi can be used to interact with WSL distributions:

```kotlin
// Get an EelDescriptor for a WSL distribution
val wslPath = Path.of("\\\\wsl.localhost\\Ubuntu\\home\\user")
val wslDescriptor = wslPath.getEelDescriptor()

// Convert to EelApi
val wslApi = wslDescriptor.toEelApi()

// Use the API to interact with the WSL distribution
val process = (wslApi as EelPosixApi).exec.spawnProcess("/bin/bash")
    .args("-c", "echo Hello from WSL")
    .eelIt()
```

## Best Practices

1. **Use EelDescriptor for lightweight operations**: Only convert to EelApi when you need to perform actual operations.

2. **Cache by EelMachine, not EelDescriptor**: When caching data that should be shared across different paths to the same environment, use `EelMachine` as the key:
   ```kotlin
   val cache: MutableMap<EelMachine, CachedData> = mutableMapOf()
   cache[descriptor.machine] = data  // Shared across all descriptors to same machine
   ```

3. **Handle errors properly**: Use try-catch blocks to handle exceptions that may be thrown during API operations.

4. **Close resources**: Always close resources like file handles, processes, and connections when you're done with them.

5. **Use platform-specific APIs when needed**: Cast to `EelPosixApi` or `EelWindowsApi` when you need platform-specific functionality.

6. **Check platform before using platform-specific features**: Use properties like `isPosix`, `isWindows`, `isMac`, etc. to check the platform before using platform-specific features.

7. **Use the builder pattern**: Many EelApi methods accept builder objects for configuring options. Use them to make your code more readable and maintainable.

8. **Prefer `toEelApi()` over `toEelApiBlocking()`**: Use the suspending version when possible to avoid blocking threads.
