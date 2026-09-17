# Eel Documentation

Eel is one API for the local machine, WSL distributions, Docker containers, and SSH hosts. It lets IntelliJ code work the same way in every environment. Eel is a word, not an acronym.

## Sections

The documentation is split by audience. Each folder has its own `README.md` index.

| Folder | Audience | Start with |
| --- | --- | --- |
| [`overview/`](overview/README.md) | Everyone | [Eel Architecture](overview/architecture.md): the goal, the model, and the Eel and IJent split. |
| [`api/`](api/README.md) | Plugin and platform developers who call Eel | [Eel API Tutorial](api/EelApi_Tutorial.md), then the [Quick Reference](api/quick-reference.md). |
| [`internal/`](internal/README.md) | Developers who change Eel or IJent | [Module Layout](internal/module-layout.md) and [Testing](internal/testing.md). |
| [`agents/`](agents/README.md) | AI agents | The key rules, the reading order, and where a new document goes. |

## Public and Internal Parts

This folder is the public part. It lives in the community edition and documents the Eel API and the Eel modules in `community/platform/eel*/`.

The IJent internals are documented in [`platform/ijent/docs/README.md`](../../../../platform/ijent/docs/README.md). That folder lives in the ultimate repository. The link works only in an ultimate checkout. It covers the Rust agent deployment, the SSH and WSL modules, and the IJent module layout.

## API Status

The Eel API is marked `@ApiStatus.Experimental`. It can change in a future version.

## Contact

File an issue in the IntelliJ IDEA issue tracker or write to the Slack channel `#ij-eel`.
