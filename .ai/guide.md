<!-- TEMPLATE:COMMENT -->
To regenerate, run `bazel run //.ai:render-guides`.
<!-- /TEMPLATE:COMMENT -->

## Project Invariants

- `*.iml` files are the source of truth. They generate the `BUILD.bazel` files. Keep an `*.iml` in the canonical form that the IDE writes: no comment, no reformat or whitespace change, no trailing newline, and no added, removed or reordered element or attribute.
<!-- IF_EDITION:ULTIMATE -->- Register a new or edited JPS module `.iml` with `bun build/jps-module.mjs register <path-to-iml> --fix-iml-eof`, then run `./build/jpsModelToBazel.cmd`. Never edit `.idea/modules.xml` by hand. The command keeps both `modules.xml` files in canonical order.<!-- /IF_EDITION:ULTIMATE --><!-- IF_EDITION:COMMUNITY -->- Register a new or edited JPS module `.iml` with `bun build/jps-module.mjs register <path-to-iml> --fix-iml-eof`, then run `./build/jpsModelToBazelCommunityOnly.cmd`. Never edit `.idea/modules.xml` by hand. The command keeps `modules.xml` in canonical order.<!-- /IF_EDITION:COMMUNITY -->
- User-visible strings belong in `*.properties` for localization.

## Language

- Read [Code Style](../.agents/skills/code-style/SKILL.md) before writing or reviewing Kotlin or Java.
- Read [Rust Code Style](../.agents/skills/rust-code-style/SKILL.md) before writing or reviewing Rust.

## Writing

Write every user-visible artifact in [ASD-STE100 Simplified Technical English](https://www.asd-ste100.org/), and keep it short. This covers a comment, KDoc, a commit message, documentation, a spec, and a report to the user. Use the active voice and a simple tense, state one topic per sentence, write positively, and use no noun cluster longer than three words. The five that catch most of the damage here:

- Keep a sentence at or under 25 words.
- Make an aside its own sentence. Do not put it between dashes.
- Keep the articles. Write "the session", not "session".
- One term per concept. Never introduce a synonym for a term the area already defines.
- Say what you left out and why. Do not pad a report to look complete.

Each kind of text has one home. Rationale and evidence for a change go in the commit message, not in a code comment. Document a declaration in KDoc on it, not in a `README.md`.

## Workspace Isolation

This repository is too large for an ad hoc Git worktree or clone. Create one only on an explicit user request, and never install a workspace manager. Isolation comes from the `branch-deck` skill: follow it before the first edit. Without it, work in the current checkout.

{{PARTIAL:module-specific}}

{{PARTIAL:code-ownership}}

## Mandatory Rules

### After Code Changes

- **Run the affected tests:** `{{TOOLS_DIR}}/bt.cmd --module <module> --filter <FQN>`, or `node --test <file>` for a `*.test.mjs` file. An FQN is required. Always name the test module. When bt refuses the module, run the `tests.cmd` command that it prints. Neither runner needs a separate `bazel build` step. A module rule can override the runner. Skip this when the plugin has no tests. See [TESTING](../.agents/skills/testing/SKILL.md).
- **Bazel compilation without tests:** to verify compilation only, run `bazel build <target>` for the affected modules. Skip this when you changed only `.js`, `.mjs`, `.md`, `.txt`, or `.json` files.
- After you change a Bazel or Starlark source (`BUILD`, `BUILD.bazel`, `MODULE.bazel`, `WORKSPACE`, `WORKSPACE.bazel`, or `*.bzl`), run `bazel run //:format.check`. If it reports a diff, run `bazel run //:format`, inspect the changes, and run the check again.
<!-- IF_EDITION:ULTIMATE -->- After you change an `*.iml`, a `BUILD.bazel`, or a `.idea/` file, run `./build/jpsModelToBazel.cmd`.
- After you change the version of a repository library, follow [Library Versions](../../.ai/library-versions.md).<!-- /IF_EDITION:ULTIMATE --><!-- IF_EDITION:COMMUNITY -->- After you change an `*.iml`, a `BUILD.bazel`, or a `.idea/` file, run `./build/jpsModelToBazelCommunityOnly.cmd`.<!-- /IF_EDITION:COMMUNITY -->

### After Writing Code

- Use `lint_files` to check the files for warnings when ijproxy or JetBrains MCP is available. Fix every warning that belongs to your changes. You can ignore an unrelated warning.

## Tools

Never use the `code-search` skill. The search tools below replace it. Recipes, the Windows rules, and the full tool inventory: [Tools Reference](./tools.md).

### Search & navigation (ijproxy preferred)

Use `search_symbol` for a class, a method, or a field. Use `search_file` to find a file by glob. Use `search_text` and `search_regex` for a string, a comment, or another match that is not a symbol.

These are `mcp__ijproxy__<name>`. Look them up in the harness deferred-tool catalog before you use a shell or a non-ijproxy fallback.

### IDE-backed semantic tools

Available through ijproxy or JetBrains MCP: `lint_files`, `get_symbol_info`, `rename`, `reformat_file`, the threading and lock checks, and the project, VCS and run-configuration tools. Prefer a real refactoring over a manual search and replace.

### Tooling rules

- Prefer ijproxy for a content or symbol **search** and for a semantic operation. Fall back to JetBrains MCP, then to `{{TOOLS_DIR}}/fd.cmd` (files) and `{{TOOLS_DIR}}/rg.cmd` (text and regex), only when ijproxy is unavailable. Pass `-H` to see a dot-directory such as `.agents/`.
<!-- IF_EDITION:ULTIMATE -->- Ask the Product DSL for a plugin model answer, not `rg` or `bazel cquery`: `bazel run //platform/buildScripts:plugin-model-tool -- --json='<request>'`. The `plugin-model-analyzer` skill holds the request shapes.<!-- /IF_EDITION:ULTIMATE -->
- Do not shell out for a file **search**, inside the repo or outside it. This is enforced: the `Glob` and `Grep` tools are denied, and so are the `grep` and `find` commands in every pipeline position.
- Prefer a spelling the allowlist knows, because a novel one asks for a prompt. Add a new entry to `{{COMMUNITY_DIR}}.ai/tool-permissions.json`, never to a harness allowlist.
- Shell is allowed where this guide documents it, and for git, build, and test. Prefer `git_status` when that tool is available. Outside the working copy it is task-scoped: read what this repo's tooling produced, or what the user or a skill named, and never survey the machine.

### Skills

Before you improvise a procedure, look for a skill that covers it in the index:

<!-- IF_EDITION:ULTIMATE -->- Skill index: [`.agents/skills/INDEX.md`](../../.agents/skills/INDEX.md)<!-- /IF_EDITION:ULTIMATE -->
<!-- IF_EDITION:COMMUNITY -->- Skill index: [`.agents/skills/INDEX.md`](../.agents/skills/INDEX.md)<!-- /IF_EDITION:COMMUNITY -->

Before a plan that adds a responsibility, run the owner check of the `duplicate-and-dead-code` skill.

{{PARTIAL:knowledge-mcps}}

## Individual Preferences

**Local Preferences:** @./.ai/local.md
