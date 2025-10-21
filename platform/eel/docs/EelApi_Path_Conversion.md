# Path Conversion in EelApi

This document explains how to convert between EelPath and java.nio.file.Path in the Eel API, which is essential for interoperability between Eel and standard Java file system APIs.

## Table of Contents

1. [Overview](#overview)
2. [Converting EelPath to NIO Path](#converting-eelpath-to-nio-path)
3. [Converting NIO Path to EelPath](#converting-nio-path-to-eelpath)
4. [Use Cases](#use-cases)

## Overview

The Eel API provides seamless conversion between Java NIO paths and EEL paths through the `EelNioBridgeService` class. This conversion is crucial for:

- Interoperating between Eel and standard Java APIs
- Working with paths in different environments (local, WSL, Docker, etc.)
- Displaying paths in a format familiar to the user

## Converting EelPath to NIO Path

The `asNioPath()` extension function converts an `EelPath` (which may represent a path on a remote machine) to a local `java.nio.file.Path`:

```kotlin
// Convert an EelPath to a NIO Path
val eelPath = EelPath.parse("/home/user", wslDescriptor)
val nioPath = eelPath.asNioPath()
// Result: Path.of("\\wsl.localhost\\Ubuntu\\home\\user")

// For Docker containers
val dockerPath = EelPath.parse("/app/data", dockerDescriptor)
val localDockerPath = dockerPath.asNioPath()
// Result: Path.of("\\docker\\containerId\\app\\data")
```

### How It Works

1. The function checks if the path is associated with the local environment (`LocalEelDescriptor`). If so, it simply converts the path to a string and creates a `java.nio.file.Path` from it.
2. For remote environments, it uses the `EelProvider` to determine the appropriate root path for the environment.
3. It then resolves the EelPath against the root path to create a local NIO path that represents the remote path.

## Converting NIO Path to EelPath

The `asEelPath()` extension function performs the reverse operation, converting a local `java.nio.file.Path` to an `EelPath`. Importantly, the resulting `EelPath` represents a path on the remote side (inside Docker, WSL, etc.), not on the local machine:

```kotlin
// Convert a NIO Path to an EelPath
val wslNioPath = Path.of("\\\\wsl$\\Ubuntu\\usr")
val wslEelPath = wslNioPath.asEelPath()
// Result: EelPath.parse("/usr", wslDescriptor)
// This represents the path "/usr" inside the WSL distribution, not on the local machine

// For Docker containers
val dockerNioPath = Path.of("\\\\docker\\containerId\\app\\data")
val dockerEelPath = dockerNioPath.asEelPath()
// Result: EelPath.parse("/app/data", dockerDescriptor)
// This represents the path "/app/data" inside the Docker container

// For local paths
val localPath = Path.of("C:\\Windows")
val localEelPath = localPath.asEelPath()
// Result: EelPath.parse("C:\\Windows", LocalEelDescriptor)
```

### How It Works

1. The function checks if the path belongs to the default file system. If not, it throws an exception.
2. It then iterates through all registered `EelProvider`s to find one that can handle the path.
3. If a provider is found, it uses the provider to get the appropriate `EelDescriptor` for the path.
4. If no provider is found, it assumes the path is local and uses `LocalEelDescriptor`.
5. It then creates an `EelPath` using the descriptor and the appropriate path format for the environment.

### Path Conversion Checks

When working with paths in different environments, it's important to verify that paths are correctly identified and converted. Here are some examples of path conversion checks from tests:

```kotlin
// Verify that a local Windows path is correctly identified as a local path
Path.of("C:\\").asEelPath().descriptor should be(localEel.descriptor)

// Verify that a WSL path using the wsl.localhost format is correctly identified as a WSL path
Path.of("\\\\wsl.localhost\\${wsl.id}\\").asEelPath().descriptor should be(wslEelApi.descriptor)

// Verify that a WSL path using the wsl$ format is correctly identified as a WSL path
Path.of("\\\\wsl$\\${wsl.id}\\").asEelPath().descriptor should be(wslEelApi.descriptor)

// Verify that WSL paths with different prefixes refer to the same file
Path.of("\\\\wsl$\\${wsl.id}\\etc").isSameFileAs(Path.of("\\\\wsl.localhost\\${wsl.id}\\etc")) should be(true)
```

These checks ensure that paths are correctly identified as belonging to the appropriate environment (local, WSL, Docker, etc.) and that path conversion works correctly across different formats and environments.

## Use Cases

Path conversion is useful in several scenarios:

### 1. Working with Standard Java APIs

Many Java APIs only accept `java.nio.file.Path` objects. By converting EelPath to NIO Path, you can use these APIs with paths from any environment:

```kotlin
// Get an EelPath for a file in a Docker container
val eelPath = EelPath.parse("/app/data/config.json", dockerDescriptor)

// Convert to NIO Path to use with standard Java APIs
val nioPath = eelPath.asNioPath()
val content = Files.readString(nioPath)
```

### 2. Determining the Environment of a Path

By converting a NIO Path to an EelPath, you can determine which environment the path belongs to:

```kotlin
// Get a path from somewhere
val path = Path.of("\\\\wsl$\\Ubuntu\\home\\user\\project")

// Convert to EelPath to get the descriptor
val eelPath = path.asEelPath()
val descriptor = eelPath.descriptor

// Check the environment
if (descriptor is WslEelDescriptor) {
    println("Path is in WSL distribution: ${descriptor.distributionName}")
} else if (descriptor is DockerEelDescriptor) {
    println("Path is in Docker container: ${descriptor.containerId}")
} else if (descriptor === LocalEelDescriptor) {
    println("Path is local")
}
```

### 3. Displaying Paths to Users

Converting between path formats allows you to display paths in a format that's familiar to the user:

```kotlin
// Get an EelPath for a file in WSL
val eelPath = EelPath.parse("/home/user/project", wslDescriptor)

// Convert to NIO Path for display
val displayPath = eelPath.asNioPath().toString()
// Result: "\\wsl$\Ubuntu\home\user\project"
```

### 4. Command-Line Arguments and Environment Variables

Path conversion is essential when passing paths as command-line arguments or environment variables to processes running in remote environments. The `asEelPath()` function ensures that paths are correctly formatted for the target environment:

```kotlin
// Convert a local JDK path to a path in the target environment for JAVA_HOME
val jdkPath = Path(javaParams.jdkPath).asEelPath().toString()
env["JAVA_HOME"] = jdkPath

// Convert a Maven executable path for use in a command line
val mavenExePath = mavenHome.resolve("bin")
                           .resolve(if (isWindows()) "mvn.cmd" else "mvn")
                           .asEelPath()
                           .toString()

// Helper function to convert any path to a target-side path string
fun String.asTargetPathString(): String = Path.of(this).asEelPath().toString()

// Use the helper function for command-line arguments
args.addAll("-s", generalSettings.userSettingsFile.asTargetPathString())
```

This is particularly important for Docker containers, where paths inside the container are completely different from paths on the host machine. For example, a path like `/usr/local/bin` inside a Docker container might correspond to a path like `\\docker\containerId\usr\local\bin` on the host machine.

By using these conversion functions, you can seamlessly work with paths across different environments, providing a consistent experience for both developers and users.