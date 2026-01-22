# Claude Code vs Codex vs JetBrains MCP (file tools)

## Scope

This note compares the file read/search/edit tool surfaces for:
- Claude Code (cc-tools.json capture)
- Codex (open-source CLI + apply_patch tool)
- JetBrains MCP server (SSE, official)
- JetBrains MCP proxy (stdio)

Note: The Claude Code tool list was captured on January 21, 2026 (see cc-tools.json). It can drift from current Claude Code builds.
Note: The proxy tool list is mode-specific (`JETBRAINS_MCP_TOOL_MODE`, default: `codex`).

## Comparison table

| Aspect          | Claude Code (cc-tools.json)          | Codex CLI                              | JB MCP server (SSE)                       | JB MCP proxy (stdio)                         |
|-----------------|--------------------------------------|----------------------------------------|-------------------------------------------|----------------------------------------------|
| Tool naming     | TitleCase (Read/Edit/Write)          | snake_case (read_file/grep/find)       | JB-style (get_file_text_by_path)          | Mode-specific snake_case                      |
| Read            | Read abs path; offset/limit          | read_file abs + indentation            | get_file_text_by_path (pathInProject)     | codex: read_file (numbered); cc: read (raw)  |
| Indentation     | No                                   | Yes                                    | No                                        | codex only                                    |
| Dir listing     | None in capture                      | list_dir                               | list_directory_tree                       | codex: list_dir; cc: none                     |
| File discovery  | Glob                                 | None (use list_dir)                    | find_files_by_glob/name                   | cc: glob; codex: find                         |
| Search output   | Grep (content/paths)                 | grep (paths)                           | search_in_files_* (entries)               | codex: grep; cc: grep                         |
| Edit/write      | Edit/Write (no MultiEdit in capture) | apply_patch                            | replace_text_in_file + create_new_file    | codex: apply_patch; cc: edit/write            |
| Path model      | Absolute paths                       | Abs for read/list; cwd for apply_patch | Project-relative                          | Abs or project-relative                        |
| apply_patch     | No                                   | Yes                                    | No                                        | codex: yes; cc: no                             |
| Tool list scope | Captured tool list                   | Tool spec list                         | Upstream MCP tools (file tools only here) | Proxy tools plus upstream tools (except blocked, replaced, or colliding names) |

## Key differences

- The proxy is not a pure pass-through: it always exposes a mode-specific proxy tool set, hides upstream tools replaced by proxy tools, and keeps the remaining upstream tools that do not collide with proxy tool names (blocked tools are filtered).
- Upstream JetBrains MCP uses project-relative paths and structured search entries; the proxy returns plain text outputs.
- Codex grep returns up to `limit` unique file paths (rg --files-with-matches) and caps `limit` at 2000; codex mode mirrors that behavior.
- Codex relies on apply_patch for edits; Claude Code uses string replacement, and the proxy follows the selected mode.
- Indentation-aware reads are Codex-style and only exposed in codex mode.

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
