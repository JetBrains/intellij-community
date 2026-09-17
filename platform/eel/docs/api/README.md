# Eel API for API Users

These documents explain how to call the Eel API from a plugin or from platform code. They do not cover the IJent internals.

## Reading Order

1. [Eel API Tutorial](EelApi_Tutorial.md) introduces the core concepts, the best practices, and the common usage patterns. Start here.
2. [Quick Reference](quick-reference.md) collects the most common calls: descriptors, process spawn, path conversion, platform detection.
3. [Path Conversion](EelApi_Path_Conversion.md) explains how to convert between NIO paths and Eel paths.
4. [NIO Integration](EelApi_NIO_Integration.md) explains `EelPathUtils`, file systems through `nio.Path`, `EelFiles` and `EelFileUtils` performance, and the JBR patches for `io.File`.
5. [LocalEelDescriptor](EelApi_LocalEelDescriptor.md) explains `LocalEelDescriptor` and `LocalEelMachine`, and when a check for the local environment is appropriate.
6. [Real-World Examples](EelApi_Real_World_Examples.md) shows how IntelliJ plugins and features use the Eel API.
7. [Opening Projects with Eel API](Opening_Projects_with_EelApi.md) explains how to open a project in WSL or Docker, with configuration options.

`images/` holds the screenshots for the last document.

## API Status

The Eel API is marked `@ApiStatus.Experimental`. It can change in a future version. Check the latest documentation and update your code when the API evolves.
