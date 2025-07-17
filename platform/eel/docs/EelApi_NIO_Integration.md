# NIO Integration in EelApi

This document explains how the Eel API integrates with Java's NIO file system and how it enables working with files in remote environments using standard Java APIs.

## Table of Contents

1. [Overview](#overview)
2. [EelPathUtils](#eelpathutils)
3. [Working with File Systems Through nio.Path](#working-with-file-systems-through-niopath)
4. [JBR Patches for io.File Compatibility](#jbr-patches-for-iofile-compatibility)

## Overview

The Eel API provides seamless integration with Java's NIO file system, allowing you to work with files in different environments (local, WSL, Docker, etc.) using standard Java APIs. This integration is achieved through several components:

- **EelPathUtils**: A utility class that provides methods for working with paths across different environments
- **Path Conversion**: Functions to convert between EelPath and java.nio.file.Path (covered in [Path Conversion in EelApi](EelApi_Path_Conversion.md))
- **JBR Patches**: Modifications to JetBrains Runtime that enable java.io.File to work with remote file systems

## EelPathUtils

`EelPathUtils` is a utility class that provides methods for working with paths in the Eel API. It helps bridge the gap between local and remote file systems by providing methods that work consistently across different environments.

### Key Methods

#### isPathLocal

```kotlin
fun isPathLocal(path: Path): Boolean
```

Determines if a path is located in the local Eel environment. This method is marked as obsolete and should be avoided in new code, as it encourages machine-dependent behavior.

**Example:**
```kotlin
// Check if a path is local
if (EelPathUtils.isPathLocal(downloadFile)) {
    // Handle local file
} else {
    // Handle remote file
}
```

#### expandUserHome

```kotlin
fun expandUserHome(eelDescriptor: EelDescriptor, path: String): Path
```

Expands the user home directory in paths, replacing "~" with the actual home directory path for the given EelDescriptor. This is useful for handling paths that use the tilde shorthand for the user's home directory.

**Example:**
```kotlin
// Expand a path with the user's home directory
val fullPath = EelPathUtils.expandUserHome(descriptor, "~/projects/myproject")
```

#### createTemporaryFile

```kotlin
fun createTemporaryFile(project: Project?, prefix: String = "", suffix: String = "", deleteOnExit: Boolean = true): Path
```

Creates a temporary file in the appropriate environment based on the project. If the project is null or local, it creates a local temporary file. Otherwise, it creates a temporary file in the project's Eel environment.

**Example:**
```kotlin
// Create a temporary file for a Git commit message
val messageFile = EelPathUtils.createTemporaryFile(project, "git-commit-", ".msg", true)
```

#### createTemporaryDirectory

```kotlin
fun createTemporaryDirectory(project: Project?, prefix: String = "", suffix: String = "", deleteOnExit: Boolean = false): Path
```

Similar to createTemporaryFile, but creates a directory instead.

#### getSystemFolder

```kotlin
fun getSystemFolder(project: Project): Path
fun getSystemFolder(eelDescriptor: EelDescriptor): Path
fun getSystemFolder(eel: EelApi): Path
```

Gets the system folder for a project, EelDescriptor, or EelApi, respectively. This is useful for storing system-specific files.

## Working with File Systems Through nio.Path

Once you have a `java.nio.file.Path` object (either created directly or converted from an `EelPath`), you can use the standard Java NIO API to work with files and directories, regardless of whether they're local or remote.

### Reading and Writing Files

```kotlin
// Read a file
val content = Files.readString(path)

// Write to a file
Files.writeString(path, "Hello, World!")

// Copy a file
Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING)

// Delete a file
Files.delete(path)
```

### Working with Directories

```kotlin
// Create a directory
Files.createDirectory(path)

// List files in a directory
Files.list(path).forEach { file ->
    println(file)
}

// Walk a directory tree
Files.walk(path).forEach { file ->
    println(file)
}
```

### File Attributes

```kotlin
// Check if a file exists
val exists = Files.exists(path)

// Get file size
val size = Files.size(path)

// Get last modified time
val lastModified = Files.getLastModifiedTime(path)

// Check if a path is a directory
val isDirectory = Files.isDirectory(path)
```

## JBR Patches for io.File Compatibility

JetBrains Runtime (JBR) includes patches that allow the older `java.io.File` API to work with remote file systems through the NIO API. This feature is disabled by default with the VM option `-Djbr.java.io.use.nio=true`.

This means that legacy code using `java.io.File` can work with remote file systems without modification. You don't need to convert between `java.io.File` and `java.nio.file.Path` in your code, and file operations behave consistently regardless of which API you use.

```kotlin
// Example: Working with a file in a Docker container using java.io.File
val containerPath = "\\\\docker\\containerId\\app\\data\\file.txt"
val file = java.io.File(containerPath)
val content = file.readText()
file.writeText("Hello from Docker!")
```

### How It Works

When the JBR patch is enabled:

1. Operations on `java.io.File` are intercepted by the JBR runtime
2. These operations are forwarded to the corresponding NIO methods
3. The NIO methods use the appropriate file system provider (which may be a custom provider for remote environments)
4. The result is returned to the original `java.io.File` operation

This allows seamless integration between legacy code using `java.io.File` and the modern NIO-based EEL API.