# Eel API Documentation

Welcome to the Eel API documentation. This documentation provides comprehensive information about the EelApi, which is a unified interface for interacting with different execution environments in IntelliJ IDEA.

## Overview

The Eel API provides a consistent way to interact with different execution environments, such as:

- Local machine
- Docker containers
- Windows Subsystem for Linux (WSL) distributions

It abstracts away the differences between these environments, allowing you to write code that works consistently across them.

## Documentation Structure

This documentation is organized into the following tutorials and guides:

1. [**EelApi Tutorial**](EelApi_Tutorial.md) - A basic introduction to EelApi, covering core concepts and basic usage patterns.

2. [**EelApi Real-World Examples**](EelApi_Real_World_Examples.md) - Provides concrete examples of how EelApi is used in actual IntelliJ IDEA plugins and features.

3. [**EelApi as Run Targets 2.0**](EelApi_as_Run_Targets_2.0.md) - Explains the relationship between EelApi and Run Targets, and the technology behind it.

4. [**EelApi Path Conversion**](EelApi_Path_Conversion.md) - Explains how to convert between NIO and EEL paths for interoperability with standard Java APIs.

5. [**EelApi LocalEelDescriptor**](EelApi_LocalEelDescriptor.md) - Explains the LocalEelDescriptor singleton and its role in representing the local environment.

6. [**EelApi NIO Integration**](EelApi_NIO_Integration.md) - Explains EelPathUtils, working with file systems through nio.Path, and JBR patches for io.File compatibility.

7. [**Opening Projects with EelApi**](Opening_Projects_with_EelApi.md) - Explains how to open projects in WSL and Docker using EelApi, with configuration options and comparisons to traditional approaches.

## Key Components

The EelApi consists of several key components:

- **EelDescriptor** - A lightweight marker for an environment where EelApi may exist.
- **EelApi** - The main interface for interacting with an environment.
- **EelExecApi** - For process execution.
- **EelTunnelsApi** - For network communication.
- **EelPlatform** - For platform-specific information.

## Getting Started

If you're new to EelApi, start with the [EelApi Tutorial](EelApi_Tutorial.md) to learn the basic concepts and usage patterns. Once you're familiar with the basics, see real-world examples in the [EelApi Real-World Examples](EelApi_Real_World_Examples.md).

## API Status

The EelApi is currently marked as `@ApiStatus.Experimental`, which means it's still under development and may change in future versions. Always check the latest documentation and be prepared to update your code as the API evolves.

## Contributing

If you find any issues or have suggestions for improving the EelApi or its documentation, please file an issue in the IntelliJ IDEA issue tracker or contact us in the Slack channel #ij-eel.