# EelApi Tutorial

## Introduction

The Eel API provides a unified interface for interacting with different execution environments, such as the local machine, Docker containers, WSL distributions, or remote SSH hosts. It abstracts away the differences between these environments, allowing you to write code that works consistently across them.

This tutorial will guide you through the core concepts of EelApi and show you how to use it effectively in your IntelliJ IDEA plugins.

## Core Concepts

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
        (eelApi as EelPosixApi).spawnProcess("/bin/ls")
            .args("-la")
            .workingDirectory("/home/user")
            .eelIt()
    }
    eelApi.platform.isWindows -> {
        // For Windows systems
        (eelApi as EelWindowsApi).spawnProcess("cmd.exe")
            .args("/c", "dir")
            .workingDirectory("C:\\Users")
            .eelIt()
    }
    else -> throw UnsupportedOperationException("Unsupported platform")
}

// The rest of the code is identical for all platforms
// Wait for process to complete
val exitCode = process.waitFor()
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
```

## Network Operations

The `EelTunnelsApi` interface provides methods for network communication:

### Connecting to a Remote Port

```kotlin
// Create a host address
val hostAddress = EelTunnelsApi.HostAddress.Builder(8080u)
    .hostname("localhost")
    .preferIPv4()
    .connectionTimeout(10.seconds)
    .build()

// Connect to the remote port
val result = eelApi.tunnels.getConnectionToRemotePort(
    EelTunnelsApi.GetConnectionToRemotePortArgs.Builder()
        .hostAddress(hostAddress)
        .build()
)

// Use the connection
result.use { connection ->
    // Configure socket options
    connection.configureSocket {
        setNoDelay(true)
    }
    
    // Get input/output channels
    val (sendChannel, receiveChannel) = connection
    
    // Send data
    sendChannel.send("Hello, World!".toByteArray())
    
    // Receive data
    val response = receiveChannel.receive(1024)
    println("Received: ${response.toString(Charsets.UTF_8)}")
}
```

### Working with Unix Sockets

```kotlin
// Listen on a Unix socket
val result = eelApi.tunnels.listenOnUnixSocket(socketPath)
val (path, sendChannel, receiveChannel) = result

// Accept connections and handle them
// ...
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
        val process = posixApi.spawnProcess("/bin/sh")
            .args("-c", "uname -a")
            .eelIt()
        
        process.stdout.readAllBytes().toString(Charsets.UTF_8).also {
            process.waitFor()
        }
    }
    eelApi.platform.isWindows -> {
        // For Windows systems
        val windowsApi = eelApi as EelWindowsApi
        val process = windowsApi.spawnProcess("cmd.exe")
            .args("/c", "systeminfo")
            .eelIt()
        
        process.stdout.readAllBytes().toString(Charsets.UTF_8).also {
            process.waitFor()
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
        val process = posixApi.spawnProcess("/bin/bash")
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
        val process = windowsApi.spawnProcess("powershell.exe")
            .args("-Command", "echo $env:USERPROFILE")
            .eelIt()
        // ...
    }
}
```

## Integration with Docker

EelApi can be used to interact with Docker containers:

```kotlin
// Get an EelDescriptor for a Docker container
val dockerDescriptor = DockerEelDescriptor(containerId)

// Convert to EelApi
val dockerApi = dockerDescriptor.toEelApi()

// Use the API to interact with the container
val process = (dockerApi as EelPosixApi).spawnProcess("/bin/ls")
    .args("-la", "/app")
    .eelIt()
```

## Integration with WSL

EelApi can be used to interact with WSL distributions:

```kotlin
// Get an EelDescriptor for a WSL distribution
val wslPath = Path.of("\\\\wsl.localhost\\Ubuntu\\home\\user")
val wslDescriptor = wslPath.getEelDescriptor()

// Convert to EelApi
val wslApi = wslDescriptor.toEelApi()

// Use the API to interact with the WSL distribution
val process = (wslApi as EelPosixApi).spawnProcess("/bin/bash")
    .args("-c", "echo Hello from WSL")
    .eelIt()
```

## Best Practices

1. **Use EelDescriptor for lightweight operations**: Only convert to EelApi when you need to perform actual operations.

2. **Handle errors properly**: Use try-catch blocks to handle exceptions that may be thrown during API operations.

3. **Close resources**: Always close resources like file handles, processes, and connections when you're done with them.

4. **Use platform-specific APIs when needed**: Cast to `EelPosixApi` or `EelWindowsApi` when you need platform-specific functionality.

5. **Check platform before using platform-specific features**: Use properties like `isPosix`, `isWindows`, `isMac`, etc. to check the platform before using platform-specific features.

6. **Use the builder pattern**: Many EelApi methods accept builder objects for configuring options. Use them to make your code more readable and maintainable.