# EelApi as Run Targets 2.0

## Introduction

EelApi represents a significant evolution in how IntelliJ-based IDEs interact with different execution environments, positioning itself as "Run Targets 2.0". This document explains the relationship between EelApi and Run Targets, the underlying technology that makes it possible, and the benefits it provides.

## From Run Targets to EelApi

Run Targets was the original approach to supporting remote execution environments in IntelliJ-based IDEs. While it provided a way to work with remote environments, it had limitations in terms of user experience, performance, and integration with the IDE.

EelApi was created to address these limitations and provide a more unified, seamless experience when working with different execution environments, particularly local isolated environments like WSL and Docker containers.

## Key Advantages of EelApi

### Unified Experience

EelApi aims to unify the local and remote worlds, ensuring that the user experience with isolated environments is no different from the local one. It eliminates the need for environment-specific checks and allows developers to write clean code without worrying about the underlying environment.

### Performance

EelApi provides significant performance improvements compared to traditional approaches:

- **Faster File System Access**: When working with WSL, EelApi is 30% faster than using the Windows file system API
- **No Freezes**: Eliminates the freezes that often occur when working with remote file systems
- **Reduced Latency**: Optimized file operations reduce the latency when reading and writing files

### Simplified Integration

Unlike Remote Development (RD), which requires downloading and setting up a separate backend, EelApi provides a more integrated approach:

- **No Client-Backend Split**: Everything works within the IDE itself
- **Fast Cold Start**: No need to download and set up a separate backend
- **Works on Any OS**: Compatible with all operating systems, including Alpine Linux

## IJent: The Technology Behind EelApi

EelApi is powered by IJent, a small agent application that provides access to remote environments. Here's how it works:

### What is IJent?

IJent is an agent application written in Rust that resides on the remote side (e.g., inside a WSL distribution or Docker container). It provides a bridge between the IDE and the remote environment, allowing the IDE to interact with the remote file system, processes, and network.

### How IJent Works

1. **Communication Protocol**: IJent uses gRPC with Protobuf for serialization, providing a stable and performant way to communicate with the IDE
2. **File System Access**: It provides direct access to the remote file system, bypassing slower protocols like 9P in the case of WSL
3. **Process Execution**: It allows the IDE to execute processes on the remote environment
4. **Network Operations**: It enables network communication with the remote environment

### Technical Challenges Solved

The development of IJent involved solving several technical challenges:

1. **Windows Firewall Issues**: Solved by running gRPC over stdio instead of TCP
2. **Symlink Handling**: Implemented correct handling of symlinks for file operations
3. **ZipFile Performance**: Optimized the reading of ZIP archives by reading in larger chunks
4. **Transport Optimization**: Used Hyper-V sockets for faster communication with WSL
5. **Throughput Improvements**: Added a separate channel for large data transfers
6. **Latency Reduction**: Implemented specialized operations to reduce the number of RPCs

## Relationship Between IJent and EelApi

While IJent and EelApi are closely related, they serve different purposes:

- **IJent** is focused on accessing remote machines. It's an agent application with a controlled lifecycle, suitable for temporary operations on environments you control.
- **EelApi** is designed to unify local and remote worlds. It's an API that abstracts away the differences between environments, allowing you to write code that works consistently across them.

In general, IJent implements the EelApi interface, providing the actual functionality that EelApi exposes to the IDE and plugins.

## Use Cases

### WSL Integration

EelApi provides seamless integration with WSL, allowing you to:

- Open projects located in WSL distributions
- Execute processes inside WSL
- Access the WSL file system with proper handling of symlinks and permissions
- Achieve better performance than using the Windows file system API

### Docker Integration

EelApi also works with Docker containers, enabling:

- Opening projects inside Docker containers
- Running processes inside containers
- Accessing the container file system
- Working with Docker-specific features

## Future Directions

While EelApi currently focuses on "local remotes" like WSL and Docker, there are plans to extend it to support "true" remotes over SSH in the future. This would provide a unified API for all types of remote environments, from local containers to remote servers.

## Conclusion

EelApi represents a significant step forward in how IntelliJ-based IDEs interact with different execution environments. By providing a unified API that works consistently across local and remote environments, it enables a more seamless and productive development experience.

As the API stabilizes, it will become available to plugin developers, allowing them to create plugins that work consistently across different environments without having to worry about the underlying details.