# ij-proxy Search: API Shape and Compatibility

This document describes the search tool surface we expose to clients and how ij-proxy bridges differences between JetBrains MCP versions.

## Goals

- Provide a small, predictable search API for clients.
- Keep behavior stable across IDE versions.
- Normalize results and apply workarounds for known upstream limitations.

## Client-Facing Search Tools (Proxy Shape)

When the upstream server does not provide `search_*` tools (or when `JETBRAINS_MCP_PROXY_DISABLE_NEW_SEARCH` is enabled), ij-proxy exposes four search tools with minimal parameters:

- `search_text(q, paths?, limit?)`
- `search_regex(q, paths?, limit?)`
- `search_file(q, paths?, includeExcluded?, limit?)`
- `search_symbol(q, paths?, limit?)`

Common semantics:

- `q` is the query. It is always a literal substring for `search_text`, always a regular expression for `search_regex`, and always a glob for `search_file`.
- `paths` is an optional list of project-relative glob filters (see Path Filters below).
- `limit` is the max number of results to return.
- `includeExcluded` (search_file only) controls whether excluded/ignored files are included when the IDE supports it.

Output shape:

- JSON with `items` and optional `more`.
- Each item is an object with `filePath` and optional `lineNumber`/`lineText`.
- `search_file` returns only `filePath`.
- `search_text` / `search_regex` return snippets (`lineNumber`, `lineText`) when available.
- `search_symbol` returns file paths and snippets when available.

## Tool Exposure and Modes

Search tools are exposed in both tool modes (`JETBRAINS_MCP_TOOL_MODE=codex` or `cc`) with the same names and parameters when ij-proxy provides the fallback shims.

You can force legacy search behavior by setting `JETBRAINS_MCP_PROXY_DISABLE_NEW_SEARCH` to any non-empty value except `0` or `false`.
When enabled, ij-proxy will ignore upstream `search_*` tools and only use the legacy search APIs when present.

- If the upstream server already exposes `search_*` tools, ij-proxy passes them through unchanged (schema + behavior) and does not expose proxy shims for those names.
- When upstream `search_*` tools are absent (or disabled), `search_text`, `search_regex`, and `search_file` are exposed via the proxy shims.
- `search_symbol` is only exposed when the upstream server provides it (ij-proxy does not emulate symbol search).

When legacy upstream tools are present, ij-proxy hides them and presents the unified `search_*` surface instead:

- `search_in_files_by_text` -> `search_text`
- `search_in_files_by_regex` -> `search_regex`
- `find_files_by_glob` -> `search_file`

## Path Filters (`paths`)

`paths` is a list of glob patterns relative to the project root.

Rules:

- Supports `!` excludes (negation).
- Trailing `/` expands to `**` (e.g., `src/` => `src/**`).
- Patterns without `/` are treated as `**/pattern`.
- Absolute paths are normalized to project-relative when they are inside the project; otherwise they are rejected.
- Empty strings are ignored.

Examples:

- `paths = ["src/**", "!**/test/**"]`
- `paths = ["**/*.kt"]`
- `paths = ["platform/"]`

## How ij-proxy and JetBrains MCP Complement Each Other

JetBrains MCP is the source of truth for indexing/search inside the IDE. ij-proxy does not implement its own index; it adapts the upstream API.

ij-proxy provides:

- A stable client surface even when upstream tool names or parameters change.
- Backward compatibility for older IDE builds.
- Result normalization and path filtering that are consistent across versions.

### New IDE Versions (Preferred)

When the upstream server exposes the new search tools directly, ij-proxy passes them through unchanged. In this mode, tool parameters and output shape are defined by the upstream server, and ij-proxy does not apply proxy-side path filtering or result normalization.

### Older IDE Versions (Compatibility)

If the new tools are not present, ij-proxy emulates them using legacy tools:

- `search_text` -> `search_in_files_by_text`
- `search_regex` -> `search_in_files_by_regex`
- `search_file` -> `find_files_by_glob`
- `search_symbol` is only exposed when the upstream server provides `search_symbol`.

For `search_file`, `includeExcluded=true` maps to `addExcluded` on legacy `find_files_by_glob` when available.

When using the proxy shims, ij-proxy applies `paths` filters locally so clients see consistent behavior across IDE versions.

## Workarounds and Normalization

Some upstream legacy tools have known limitations. ij-proxy compensates for them when using the proxy shims:

- Regex directory scope: some IDE versions ignore `directoryToSearch` for regex searches. ij-proxy always post-filters regex results by path to keep results correct.
- Path-aware globs: upstream file masks match filenames only. For path-aware patterns (like `src/**/Foo*.java`), ij-proxy applies glob matching on full paths after the search.

These workarounds are intentionally kept in ij-proxy so clients get stable behavior without needing version checks.

## Relation to Legacy Tools

The `find` tool still exists for older client conventions, but the new search surface is the primary API going forward.

If you are implementing a client, use the new tools described above for predictable behavior across IDE versions.
