---
name: eel
description: Use IntelliJ EEL APIs for process execution, paths, WSL, or Docker.
---

# EEL (Execution Environment Layer) Skill

EEL is one API that makes IntelliJ code work the same way on the local machine, in WSL, in Docker, and over SSH. It replaces scattered WSL checks, `java.io.File`, `System.getenv`, `ProcessBuilder`, and `SystemInfo` with a single abstraction.

Read `community/platform/eel/docs/agents/README.md` first. It holds the rules, the reading order, and where a new document goes.

## Key Rules

- **Use `nio.Path`, not `java.io.File`.** NIO file operations go through the EEL file system providers, so `Files.readString(path)` and `Files.walk(path)` work in WSL and Docker without extra code.
- **Use `EelApi.exec` for process execution, not `ProcessBuilder`.** The process then runs in the correct environment.
- **Use `EelPlatform` for OS detection, not `SystemInfo`.** `SystemInfo` reflects the IDE host machine, not the target environment.
- **`localEel` always represents the IDE host machine.** Use it only when you need the local environment. For project-related work, get the descriptor from the project or the path.

## Documentation

- Agent instructions: `community/platform/eel/docs/agents/README.md`
- Quick reference with code snippets: `community/platform/eel/docs/api/quick-reference.md`
- Tutorial: `community/platform/eel/docs/api/EelApi_Tutorial.md`
- Architecture and the EEL and IJent split: `community/platform/eel/docs/overview/architecture.md`
- IJent internals (ultimate checkout only): `platform/ijent/docs/README.md`
