# EelApi Real-World Examples

This document provides an overview of how the Environment Execution Layer (EEL) API is used in IntelliJ IDEA plugins and features. It builds on the concepts introduced in the [EelApi Tutorial](EelApi_Tutorial.md).

## Table of Contents

1. [Build Tools Integration](#build-tools-integration)
2. [Version Control Integration](#version-control-integration)
3. [Rust Migration](#rust-migration)
4. [Terminal Migration](#terminal-migration)

## Build Tools Integration

EelApi enables IntelliJ IDEA to run build tools like Gradle and Maven in different environments (local, Docker, WSL) with a unified approach. This is crucial for projects that need to be built in specific environments.

### Environment-Aware Build Execution

The key pattern in build tool integration is checking if the environment is local or remote, and adapting the execution strategy accordingly. The `EelTargetEnvironmentConfigurationProvider` class implements this pattern for Gradle, skipping custom execution for local environments and providing environment-specific execution for remote environments.

For remote environments, the provider:
- Determines the appropriate Gradle executable based on the platform (gradle.bat for Windows, gradle for Posix)
- Verifies that the executable exists in the remote environment
- Creates a custom execution configuration that handles remote execution

### Path Conversion for Build Tools

When executing build tools in remote environments, it's essential to convert paths between local and remote representations. The `EelMavenRemoteProcessSupportFactory` handles this for Maven by:
- Converting environment variables to use remote paths
- Converting the working directory to a remote path
- Executing Maven in the remote environment using the appropriate paths

## Version Control Integration

EelApi enables version control systems like Git to work seamlessly across different environments, ensuring that VCS operations work consistently whether the project is local, in a Docker container, or in WSL.

### Environment-Aware Git Operations

The key benefit of using EelApi with Git is that it automatically adapts to the environment where the project is located. The `GitRecentProjectsBranchesProvider` demonstrates this by:
- Getting the environment descriptor for the project
- Converting it to a full EelApi instance
- Checking if the project is a Git repository by looking for a .git directory
- Executing Git commands in the appropriate environment
- Parsing the results to extract branch information

This approach ensures that Git operations work consistently across all environments without requiring environment-specific code.

## Rust Migration

RustRover uses EelApi to enable seamless Rust development across different environments (local, Docker, WSL). This allows Rust developers to work with projects located in any environment without having to worry about the underlying details.

### Environment-Aware Rust Toolchain

The key component is the `RsEelToolchain` class, which provides environment-specific implementations for Rust toolchain operations. It handles:
- Using the appropriate path separator for the environment
- Converting paths between local and remote representations
- Determining the correct executable name based on the platform
- Checking if EelApi can be used for a project or path
- Fetching environment variables from the appropriate environment

The `RsEelToolchainProvider` creates instances of `RsEelToolchain` when appropriate, allowing RustRover to:
1. **Detect the Environment**: Automatically determine if a Rust project is in a local or remote environment
2. **Use the Right Tools**: Execute Rust tools (cargo, rustc, rustup) in the appropriate environment
3. **Handle Path Conversion**: Convert paths between local and remote representations
4. **Fetch Environment Variables**: Get the correct environment variables for the project environment

## Terminal Migration

IntelliJ IDEA's terminal integration uses EelApi to provide a consistent terminal experience across different environments. This allows users to work with terminals in Docker containers, WSL distributions, or remote SSH hosts just as easily as with local terminals.

### Environment-Aware Shell Detection

The terminal plugin uses EelApi to determine the appropriate shell to use based on the environment. The `TerminalProjectOptionsProvider` handles this by:
- Using different logic for Windows vs. Posix environments
- Getting the shell from environment variables when possible
- Falling back to platform-specific defaults (/bin/zsh for macOS, /bin/bash for Linux, etc.)

### Command Execution via EelApi

The terminal plugin executes commands using EelApi, ensuring they run in the appropriate environment. The `ShellRuntimeContextReworkedImpl` demonstrates this by:
- Getting the EelDescriptor for the current directory
- Converting it to a full EelApi instance
- Executing commands in the appropriate environment
- Collecting and parsing the results

This approach allows the terminal plugin to:
1. **Detect the Environment**: Automatically determine if a terminal should run in a local or remote environment
2. **Use the Right Shell**: Start the appropriate shell for the environment
3. **Execute Commands**: Run commands in the appropriate environment
4. **Handle Path Conversion**: Convert paths between local and remote representations

## Conclusion

These real-world examples demonstrate how EelApi enables IntelliJ IDEA plugins to work seamlessly across different environments. The key benefits include:

1. **Environment Abstraction**: Code written against EelApi works in any environment (local, Docker, WSL) without environment-specific logic.

2. **Path Handling**: EelApi automatically handles path conversion between different environments, making it easy to work with files across environments.

3. **Process Execution**: Running processes in different environments is simplified with a consistent API, regardless of the underlying OS.

By using EelApi, plugin developers can focus on their functionality rather than the complexities of different environments, resulting in a consistent user experience across all supported platforms.