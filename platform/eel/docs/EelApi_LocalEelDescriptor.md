# LocalEelDescriptor

`LocalEelDescriptor` represents the environment where the IntelliJ IDE is installed. It's a singleton object that identifies the local machine's environment.

## Overview

While the Eel API provides a way to interact with various environments (like Docker containers or WSL distributions), `LocalEelDescriptor` specifically represents the native environment of the IDE itself.

Local files and local projects will return `LocalEelDescriptor` when queried for their environment. This makes it easy to determine whether a file or project is on the local machine or in a remote/containerized environment.

## Role in the EEL API

`LocalEelDescriptor` serves as the default environment when no other environment is applicable. For example, when a path doesn't match any known remote environment pattern, it's assumed to be a local path and associated with `LocalEelDescriptor`.

This allows the Eel API to provide a unified interface for both local and remote operations, making it easier to write code that works consistently across different environments.

## Usage Examples

### Checking if a File Belongs to the Local Environment

```kotlin
// Check if a path is on the local machine
val isLocal = path.getEelDescriptor() == LocalEelDescriptor

// Check if a path is in a remote environment
val isRemote = path.getEelDescriptor() != LocalEelDescriptor

// Conditional behavior based on environment
if (path.getEelDescriptor() == LocalEelDescriptor) {
    // Handle local file
} else {
    // Handle remote file
}
```