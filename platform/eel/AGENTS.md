# Eel

Eel is one API for the local machine, WSL, Docker, and SSH. Eel is a word, not an acronym. The modules `community/platform/eel*/` hold the API, the NIO bridge, and the local implementation.

Read [docs/agents/README.md](docs/agents/README.md) first. It holds the key rules, the reading order, and where a new document goes.

## Documentation

| Folder | Audience |
| --- | --- |
| [docs/overview/](docs/overview/README.md) | Everyone: architecture and goals |
| [docs/api/](docs/api/README.md) | Developers who call Eel |
| [docs/internal/](docs/internal/README.md) | Developers who change Eel: module layout, tests |
| [docs/agents/](docs/agents/README.md) | AI agents |

Before you compare `EelDescriptor` values or convert one into an `EelMachine` or `EelApi`, read [Descriptor and Machine Identity](docs/api/eel-descriptor-and-machine.md).

Before you write `if (x is Something)`, `x as? Something`, `if (x == Enum.Case)`, or `else ->` on a sealed type or an enum, read [Exhaustive `when`](docs/api/exhaustive-when.md). This covers `SafeDeferred.State`, `EelOsFamily`, `EelPlatform`, and every `private` or `internal` sealed type in Eel and IJent code.

The IJent internals are documented in `platform/ijent/docs/` in an ultimate checkout.

## Tests

`./tests.cmd --module intellij.platform.eel.tests --test <FQN>`
