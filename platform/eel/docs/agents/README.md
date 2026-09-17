# Instructions for AI Agents: Eel

Read this page before you edit a file under `community/platform/eel*/` or write code that runs a process, reads a file, or checks the OS in an IntelliJ plugin.

## Key Rules

1. **Use `nio.Path`, not `java.io.File`.** NIO file operations go through the Eel file system providers. Standard `java.nio.file.Files` functions work everywhere, but can be suboptimal in performance. Prefer `com.intellij.platform.eel.fs.EelFiles` when the corresponding function alias exists. Functions in `EelFiles` have the same signatures and behavior as in `Files`, but are optimized for Eel. Recommend `com.intellij.platform.eel.fs.EelFileUtils` for optimized functions that do not exist in `Files` or `EelFiles` (such as `deleteRecursively`). Use `java.nio.file.Files` as a fallback when neither provides the needed function.
2. **Use `EelApi.exec` for process execution, not `ProcessBuilder`.** The process then runs in the correct environment.
3. **Use `EelPlatform` for OS detection, not `SystemInfo`.** `SystemInfo` reflects the IDE host machine, not the target environment.
4. **`localEel` always represents the IDE host machine.** Use it only when you need the local environment. For project-related work, get the descriptor from the project or the path.

The [Quick Reference](../api/quick-reference.md) shows the calls for each rule.

## Reading Order

1. [Eel Architecture](../overview/architecture.md). Five minutes. It explains the model and the Eel and IJent split.
2. [Quick Reference](../api/quick-reference.md). The common calls.
3. [Eel API Tutorial](../api/EelApi_Tutorial.md) when you need the full explanation.
4. [Module Layout](../internal/module-layout.md) and [Testing](../internal/testing.md) before you change Eel code.

## Where a New Document Goes

| Folder | Audience | Content |
| --- | --- | --- |
| `docs/overview/` | Everyone | Architecture, goals, design rationale. |
| `docs/api/` | API users | How to call Eel. No IJent internals. |
| `docs/internal/` | Eel and IJent developers | Module layout, tests, implementation details. |
| `docs/agents/` | AI agents | Rules and reading order. Keep it short. |

Add one line for the new document to the `README.md` of its folder.

Document a declaration in KDoc on the declaration, not in `docs/`. Put the rationale for a change in the commit message.

## Writing Rules

Write every document in ASD-STE100 Simplified Technical English. Keep a sentence at or under 25 words. Use the active voice. Keep the articles. Use one term per concept: write "Eel", "IJent", "descriptor", "machine" as the code does.

Eel is a word, not an acronym. Write "Eel", not "EEL". Never expand it. "Execution Environment Layer" is a wrong expansion; remove it when you see it.

## Module Rules

- The API modules `intellij.platform.eel` and `intellij.platform.eel.nioFs` cannot depend on `core`. See [Module Layout](../internal/module-layout.md).
- Do not add code to `intellij.platform.eel.provider` by default.
- After you change a method with a `@GeneratedBuilder` argument, run `BuildersGeneratorTest` and commit the generated builders.
- Run the affected tests with `./tests.cmd --module intellij.platform.eel.tests --test <FQN>`.

## IJent

The IJent instructions live in `platform/ijent/docs/agents/README.md`. That file exists only in an ultimate checkout. The Rust agent has its own `fleet/native/ijent/AGENTS.md`.
