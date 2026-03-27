# Spec: `apply_patch` Compatibility

## Status

- Implemented in JetBrains MCP `PatchApplyEngine` and ij-proxy `apply-patch` handler.

## Summary

- Canonical Codex `apply_patch` format remains the primary format.
- Compatibility extensions are supported for mixed-client ecosystems that emit unified git diffs and strict `@@` pair block patches.
- This spec documents accepted inputs and deterministic operation mapping without changing tool names or API surface.

## Accepted Input Forms

- Codex patch format wrapped with:
  - `*** Begin Patch`
  - `*** End Patch`
- Raw unified git diff (unwrapped), for example beginning with `diff --git`.
- Unified git diff wrapped inside `*** Begin Patch` / `*** End Patch`.

## Git Diff Operation Mapping

- `/dev/null -> path` or `new file mode` maps to add-file operation.
- `path -> /dev/null` maps to delete-file operation.
- `path -> path` with hunks maps to update-file operation.
- `rename from <old>` + `rename to <new>` without hunks maps to update/move operation with empty hunks.
- Rename-only operations preserve original content (move without rewrite).

## Hunk Parsing Behavior

### Canonical `@@` textual header behavior

- Textual headers such as `@@ def sample()` remain search hints.
- Existing Codex hunk semantics are unchanged.

### Unified-diff coordinate headers

- Pure coordinate headers are treated as metadata, not search hints.
- If a hunk header is coordinate-only, stored header becomes `null`.
- Strict detection regex:
  - `^@@+\s*-\d+(?:,\d+)?\s+\+\d+(?:,\d+)?\s*@@+$`
- Matches examples:
  - `@@ -1,3 +1,4 @@`
  - `@@ -9 +10 @@`
  - `@@@ -48,6 +48,7 @@@`
- Non-matches (remain textual hints):
  - `@@ def sample()`
  - `@@ class Foo @@`

### Strict `@@` pair block mode

- Triggered only when hunk header is exactly `@@` and following lines are block content.
- Structure is two unprefixed blocks separated by a second `@@` delimiter:
  - first block = old text
  - second block = new text
- Parser materializes first block as `-` lines and second block as `+` lines.
- Missing second delimiter fails with:
  - `Strict @@ pair hunk requires second @@ delimiter`
- Literal lines beginning with `-` or `+` inside these blocks are treated as content, not hunk prefixes.

## Compatibility Guarantees

- No tool-name change (`apply_patch` remains the interface).
- No schema contract change beyond documented input compatibility.
- Codex canonical format remains supported unchanged.
- Textual `@@ <hint>` behavior remains intact.

## Test Coverage

- JetBrains MCP parser tests in `PatchApplyEngineTest` cover:
  - raw/wrapped unified git diff parsing,
  - rename-only git diff mapping,
  - strict `@@` pair mode and missing-delimiter failure,
  - textual `@@` guard behavior.
- JetBrains MCP toolset tests in `PatchToolsetTest` cover:
  - strict `@@` pair apply,
  - wrapped unified git diff apply,
  - raw rename-only apply preserving content.
- ij-proxy tests cover the same compatibility surface in unit and integration suites.

## Validation Commands

- `./tests.cmd --module intellij.mcpserver.tests --test com.intellij.mcpserver.toolsets.general.PatchApplyEngineTest`
- `./tests.cmd --module intellij.mcpserver.tests --test com.intellij.mcpserver.toolsets.PatchToolsetTest`
- `cd community/build/mcp-servers/ij-proxy && bun run build && bun test`
