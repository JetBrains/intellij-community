# Eel Internals

These documents are for developers who change Eel or IJent. API users do not need them.

- [Module Layout](module-layout.md) lists the `intellij.platform.eel*` modules, the dependency direction, and where new code goes.
- [API Binary Compatibility Rules](api-compatibility.md) explains why Kotlin `data class` declarations and default arguments are prohibited in API, even when marked `@ApiStatus.Internal`.
- [Mutable State Invariants](mutable-state-invariants.md) explains when and how to model complex mutable state with sealed types and finite state machines so invalid combinations cannot be represented.
- [Testing](testing.md) lists the test modules, the `./tests.cmd` command, and the two test frameworks: `@TestApplicationWithEel` for Eel API users and `EelFixture` for Eel and IJent developers.
- [NIO Routing Internals](nio-routing.md) explains how `MultiRoutingFileSystem` becomes the default file system and how a backend mounts an IJent file system under a prefix. It also shows how to open an IJent NIO file system without the routing.
- [Exhaustive `when`](../api/exhaustive-when.md) applies to Eel and IJent code too. Check a sealed type or an enum with an exhaustive `when`, not with `if` or `else`.

The IJent side, including the Rust agent, the deployment strategies, and the SSH and WSL modules, is documented in `platform/ijent/docs/`. That folder exists only in an ultimate checkout.
