# Eel API Documentation

Welcome to the Eel API documentation. This documentation provides comprehensive information about the EelApi, which is a unified interface for interacting with different execution environments in IntelliJ IDEA.

## Overview

The Eel API provides a consistent way to interact with different execution environments, such as:

- Local machine
- Docker containers
- Windows Subsystem for Linux (WSL) distributions

It abstracts away the differences between these environments, allowing you to write code that works consistently across them.

## Core Concepts

### EelDescriptor vs EelMachine

Understanding these two fundamental concepts is crucial for working effectively with the Eel API:

**EelDescriptor** - A specific path-based access to an environment:
- Represents a particular way to access an environment (e.g., `\\wsl$\Ubuntu` vs `\\wsl.localhost\Ubuntu`)
- Lightweight identifier that can be obtained quickly
- Different descriptors may point to the same physical machine
- Used when you need to work with specific paths

**EelMachine** - The physical or logical host:
- Represents the actual machine (container, distribution, remote host)
- Multiple descriptors can resolve to the same machine
- Used for caching and resource pooling across all access paths to the same environment
- Contains platform information (OS family, architecture)

**Example**:
```kotlin
// Two different descriptors
val desc1 = Path.of("\\\\wsl$\\Ubuntu\\home").getEelDescriptor()
val desc2 = Path.of("\\\\wsl.localhost\\Ubuntu\\home").getEelDescriptor()

// But they point to the same machine
desc1.machine === desc2.machine  // true

// Use machine for shared caching
val cache: Map<EelMachine, Data> = mutableMapOf()
cache[desc1.machine] = data  // Accessible via desc2.machine as well
```

## Getting Started

**New to EelApi?** Start with the [EelApi Tutorial](EelApi_Tutorial.md) for a comprehensive introduction to core concepts and usage patterns.

## Documentation Structure

This documentation is organized into the following tutorials and guides:

1. [**EelApi Tutorial**](EelApi_Tutorial.md) - A comprehensive introduction to EelApi, covering core concepts, best practices, and common usage patterns.

2. [**EelApi LocalEelDescriptor**](EelApi_LocalEelDescriptor.md) - Explains LocalEelDescriptor and LocalEelMachine, their role in representing the local environment, and when it's appropriate to check for local environments.

3. [**EelApi Path Conversion**](EelApi_Path_Conversion.md) - Explains how to convert between NIO and EEL paths for interoperability with standard Java APIs.

4. [**EelApi NIO Integration**](EelApi_NIO_Integration.md) - Explains EelPathUtils, working with file systems through nio.Path, and JBR patches for io.File compatibility.

5. [**EelApi Real-World Examples**](EelApi_Real_World_Examples.md) - Provides concrete examples of how EelApi is used in actual IntelliJ IDEA plugins and features.

6. [**Opening Projects with EelApi**](Opening_Projects_with_EelApi.md) - Explains how to open projects in WSL and Docker using EelApi, with configuration options and comparisons to traditional approaches.

7. [**EelApi as Run Targets 2.0**](EelApi_as_Run_Targets_2.0.md) - Explains the relationship between EelApi and Run Targets, the technology behind IJent, and the architectural advantages.

## Key Components

The EelApi consists of several key components:

### Core Interfaces

- **EelDescriptor** - A lightweight marker for a specific path-based access to an environment
- **EelMachine** - Represents the physical or logical host machine
- **EelApi** - The main interface for interacting with an environment
- **EelExecApi** - For process execution
- **EelTunnelsApi** - For network communication
- **EelPlatform** - For platform-specific information (OS, architecture)
- **EelUserInfo** - Information about the user in the environment

### Platform-Specific Interfaces

- **EelPosixApi** - For Unix-like systems (Linux, macOS, FreeBSD)
- **EelWindowsApi** - For Windows systems
- **LocalEelApi** - Marker interface for the local machine

## Quick Reference

### Getting an EelDescriptor

```kotlin
// From a path
val descriptor = Path.of("\\\\wsl.localhost\\Ubuntu\\home\\user").getEelDescriptor()

// From a project
val descriptor = project.getEelDescriptor()
```

### Converting to EelApi

```kotlin
// Suspending (preferred)
val eelApi = descriptor.toEelApi()

// Blocking
val eelApi = descriptor.toEelApiBlocking()

// For local environment (cached singleton)
val localApi = localEel
```

### Running a Process

```kotlin
val process = eelApi.exec.spawnProcess("git")
    .args("--version")
    .eelIt()

val exitCode = process.exitCode.await()
val output = process.stdout.readAllBytes().toString(Charsets.UTF_8)
```

### Path Conversion

```kotlin
// NIO Path → EelPath
val eelPath = nioPath.asEelPath()

// EelPath → NIO Path  
val nioPath = eelPath.asNioPath()
```

## API Status

The EelApi is currently marked as `@ApiStatus.Experimental`, which means it's still under development and may change in future versions. Always check the latest documentation and be prepared to update your code as the API evolves.

## Contributing

If you find any issues or have suggestions for improving the EelApi or its documentation, please file an issue in the IntelliJ IDEA issue tracker or contact us in the Slack channel #ij-eel.

## Architecture Notes

The Eel API is built on top of **IJent** (IntelliJ Agent), a Rust-based agent that runs in remote environments.
