# Eel Internals

These documents are for developers who change Eel or IJent. API users do not need them.

- [Module Layout](module-layout.md) lists the `intellij.platform.eel*` modules, the dependency direction, and where new code goes.
- [Testing](testing.md) lists the test modules, the `./tests.cmd` command, and the two test frameworks: `@TestApplicationWithEel` for Eel API users and `EelFixture` for Eel and IJent developers.
- [NIO Routing Internals](nio-routing.md) explains how `MultiRoutingFileSystem` becomes the default file system and how a backend mounts an IJent file system under a prefix. It also shows how to open an IJent NIO file system without the routing.

The IJent side, including the Rust agent, the deployment strategies, and the SSH and WSL modules, is documented in `platform/ijent/docs/`. That folder exists only in an ultimate checkout.
