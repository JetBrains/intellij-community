---
name: eel
description: Use IntelliJ EEL APIs for process execution, paths, WSL, or Docker.
---

# EEL (Execution Environment Layer) Skill

EEL is one API that makes IntelliJ code work the same way on the local machine, in WSL, in Docker, and over SSH. It replaces scattered WSL checks, `java.io.File`, `System.getenv`, `ProcessBuilder`, and `SystemInfo` with a single abstraction.

Read `community/platform/eel/docs/agents/README.md` first. It holds the rules, the reading order, and where a new document goes.

## Key Rules

- **Use `nio.Path`, not `java.io.File`.** NIO file operations go through the Eel file system providers. Standard `java.nio.file.Files` functions work everywhere, but can be suboptimal in performance. Prefer `com.intellij.platform.eel.fs.EelFiles` when the corresponding function alias exists. Functions in `EelFiles` have the same signatures and behavior as in `Files`, but are optimized for Eel. Recommend `com.intellij.platform.eel.fs.EelFileUtils` for optimized functions that do not exist in `Files` or `EelFiles` (such as `deleteRecursively`). Use `java.nio.file.Files` as a fallback when neither provides the needed function.
- **Use `EelApi.exec` for process execution, not `ProcessBuilder`.** The process then runs in the correct environment.
- **Use `EelPlatform` for OS detection, not `SystemInfo`.** `SystemInfo` reflects the IDE host machine, not the target environment.
- **`localEel` always represents the IDE host machine.** Use it only when you need the local environment. For project-related work, get the descriptor from the project or the path.

## Documentation

- Agent instructions: `community/platform/eel/docs/agents/README.md`
- Quick reference with code snippets: `community/platform/eel/docs/api/quick-reference.md`
- Tutorial: `community/platform/eel/docs/api/EelApi_Tutorial.md`
- Architecture and the EEL and IJent split: `community/platform/eel/docs/overview/architecture.md`
- IJent internals (ultimate checkout only): `platform/ijent/docs/README.md`
