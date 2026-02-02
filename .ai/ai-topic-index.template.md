# IntelliJ IDEA Project AI Reference Index

AGENTS/CLAUDE define the mandatory rules. This index is only a map to deeper docs.

## How to Use This Index

- If a module has its own AGENTS/CLAUDE, follow the most specific one.
- Pick the narrowest topic below that matches the task.
- Prefer `./topics/*` for rules and workflows; use `docs/IntelliJ-Platform/*` for API detail and design rationale.

## Reference Map

### Core workflow (most common)

- Module dependencies: [module-dependencies.md](./topics/module-dependencies.md). Adding deps, analyzing/untangling module dependencies.
- Code style: [code-style.md](./topics/code-style.md). Kotlin/Java formatting, build-scripts conventions.
- Commits: [commits.md](./topics/commits.md). Commit messages, protected branches.
- Running tests: [TESTING.md](./topics/TESTING.md). Running tests outside IDE.
- Writing tests: [writing-tests.md](./topics/writing-tests.md). `@TestApplication`, fixtures, EDT tests.
- Test internals: [TESTING-internals.md](./topics/TESTING-internals.md). `tests.cmd` failures, test discovery, Bazel targets.
- Debugging: [debugging.md](./topics/debugging.md). idea.log analysis, runtime issues.
- Safe push: [safe-push.md](./topics/safe-push.md). Protected branches, CLI: `./safePush.cmd HEAD:master`.
- Beads task tracking: [beads-guide.md](../../.ai/beads-guide.md). Tracking work in Beads.
- Plugin Model Analyzer MCP: [plugin-model-analyzer.md](../../.ai/plugin-model-analyzer.md). Module structure, product composition.

### Platform and features

- API design: [API design (Platform Docs)](../../docs/IntelliJ-Platform/1_API/directory.md). New/evolving APIs, backward compatibility, deprecation.
- Actions: [actions.md](./topics/actions.md). Adding/modifying actions, plugin.xml registration.
- Registry: [registry.md](./topics/registry.md). Registry keys in plugin.xml, `Registry.is()` API, test values, per-product overrides.
- Settings: [configurable.md](../docs/configurable.md). Settings panels, `Configurable` interface.
- SSR: [SSR.md](./topics/SSR.md). Structural search inspections, code templates.
- Remote dev: [Remote-Dev.md](./topics/Remote-Dev.md). Frontend/backend separation, remote IDE.

{{PARTIAL:guidelines-ultimate-extras}}
