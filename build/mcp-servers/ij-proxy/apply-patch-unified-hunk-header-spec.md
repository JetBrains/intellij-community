# Spec: Unified-Diff Coordinate Headers in `apply_patch`

## Status

- Implemented in JetBrains MCP `PatchApplyEngine` and ij-proxy `apply-patch` handler.

## Problem

- Canonical `apply_patch` hunks use `@@` with optional textual context (for example `@@ def foo()`), and engines treat that trailing text as a search hint.
- In practice, some LLM clients emit unified-diff coordinate headers like `@@ -1,3 +1,4 @@` or `@@@ -48,6 +48,7 @@@`.
- Those coordinate payloads are metadata, not real file lines.
- If consumed as search hints, the engine searches for literals like `-1,3 +1,4 @@` and fails with `Hunk context not found`.

## Why Add This Over Canonical Codex Format

- This is a compatibility extension for mixed-client ecosystems, not a replacement of Codex format.
- We keep Codex-style textual headers unchanged.
- We only special-case pure unified-diff coordinate payloads because they are semantically line-number metadata.
- Codex grammar does not require coordinate parsing, but accepting it here improves interoperability with no API change.

## Behavior

- On hunk header lines (`@@...`), engines run `stripUnifiedDiffHeader(trimmed)` before storing `header`.
- If the line is a pure unified-diff coordinate header, `header` becomes `null`.
- Otherwise, existing behavior is preserved (strip first `@@` marker and keep trailing text as header hint).

### Coordinate Detection Rule (Strict)

- Regex: `^@@+\s*-\d+(?:,\d+)?\s+\+\d+(?:,\d+)?\s*@@+$`
- Matches examples:
  - `@@ -1,3 +1,4 @@`
  - `@@ -9 +10 @@`
  - `@@@ -48,6 +48,7 @@@`
- Does not match textual hints such as:
  - `@@ def sample()`
  - `@@ class Foo @@`

## Non-Regression Guarantees

- No tool schema or public API changes.
- Textual `@@ <hint>` behavior remains intact.
- Hunk matching and replacement logic is unchanged.
- Only coordinate-only headers are normalized to `null`.

## Tests

- Parse test: coordinate headers are stripped to `header = null`.
- End-to-end apply test: unified coordinate headers apply successfully.
- Guard test: textual `@@ <hint>` headers still work.

## Validation Commands

- `./tests.cmd -Dintellij.build.test.patterns=com.intellij.mcpserver.toolsets.general.PatchApplyEngineTest`
- `cd community/build/mcp-servers/ij-proxy && bun run build && bun test`

