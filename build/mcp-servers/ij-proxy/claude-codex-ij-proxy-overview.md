# Claude Code vs Codex vs JetBrains MCP (file tools)

## Scope

This note compares the file read/search/edit tool surfaces for:
- Claude Code (cc-tools.json capture)
- Codex (open-source CLI + apply_patch tool)
- JetBrains MCP server (SSE, official)
- JetBrains MCP proxy (stdio)

Note: The Claude Code tool list was captured on January 21, 2026 (see cc-tools.json). It can drift from current Claude Code builds.
Note: The proxy tool list is fixed.
Note: The proxy exposes search shims only when upstream does not provide the same `search_*` tools; otherwise it passes the upstream tools through.

## Comparison table

- Tool naming: Claude Code uses TitleCase (`Read`/`Edit`/`Write`), Codex uses snake_case (`read_file`/`grep`/`find`), JetBrains MCP uses JB-style names (`get_file_text_by_path`), and ij-proxy uses snake_case.
- Read: Claude Code `Read` supports absolute path + offset/limit; Codex uses `read_file` with indentation mode; JetBrains MCP uses `get_file_text_by_path`; ij-proxy exposes `read_file`.
- Indentation: available in Codex; not available in native Claude Code or native JetBrains MCP; exposed by ij-proxy via `read_file`.
- Directory listing: Codex has `list_dir`; JetBrains MCP has `list_directory_tree`; Claude Code capture had none; ij-proxy exposes `list_dir`.
- File discovery: Claude Code uses glob; JetBrains MCP uses `search_file`; Codex relies on directory listing; ij-proxy exposes `list_dir` and `search_file`.
- Search output: Claude Code uses Grep; Codex uses grep/find; JetBrains MCP uses structured `search_*`; ij-proxy normalizes to `search_text`/`search_regex`/`search_file`/`search_symbol` when shims are active.
- Edit/write: Claude Code uses `Edit`/`Write`; Codex uses `apply_patch`; JetBrains MCP uses `replace_text_in_file` + `create_new_file`; ij-proxy uses `apply_patch`.
- Path model: Claude Code and Codex read tools use absolute paths; JetBrains MCP tools are project-relative; ij-proxy accepts absolute or project-relative paths.
- `apply_patch`: supported in Codex and ij-proxy; not supported in native Claude Code or native JetBrains MCP.
- Tool list scope: Claude Code table is a point-in-time capture, Codex is from tool specs, JetBrains MCP is upstream tool list, and ij-proxy combines proxy tools with non-conflicting upstream tools.

## Key differences

- The proxy is not a pure pass-through: it exposes a fixed proxy tool set unless the upstream already provides the same tool name, hides upstream tools replaced by proxy tools, and keeps the remaining upstream tools that do not collide with proxy tool names (blocked tools are filtered).
- Upstream JetBrains MCP uses project-relative paths and structured search entries; the proxy returns plain text outputs.
- The proxy exposes `search_*` shims only when upstream does not provide the same tools; otherwise it passes upstream search tools through unchanged.
- Codex relies on apply_patch for edits; Claude Code uses string replacement, and the proxy follows Codex-style edit flows.
- Indentation-aware reads are Codex-style and exposed via `read_file`.

## References

- Claude Code capture: `community/build/mcp-servers/ij-proxy/cc-tools.json`
- JetBrains MCP server: `community/plugins/mcp-server/src/com/intellij/mcpserver/toolsets/general/TextToolset.kt`,
  `community/plugins/mcp-server/src/com/intellij/mcpserver/toolsets/general/FileToolset.kt`
- JetBrains MCP proxy: `community/build/mcp-servers/ij-proxy/README.md`,
  `community/build/mcp-servers/ij-proxy/project-path.ts`,
  `community/build/mcp-servers/ij-proxy/stream-transport.ts`,
  `community/build/mcp-servers/ij-proxy/proxy-tools/tooling.ts`,
  `community/build/mcp-servers/ij-proxy/ij-mcp-proxy.ts`
- Codex CLI (local checkout): `~/Downloads/codex-main/codex-rs/core/src/tools/spec.rs`,
  `~/Downloads/codex-main/codex-rs/core/src/tools/handlers/apply_patch.rs`
- OpenAI docs:
  - https://developers.openai.com/codex/cli
  - https://platform.openai.com/docs/guides/tools-apply-patch
