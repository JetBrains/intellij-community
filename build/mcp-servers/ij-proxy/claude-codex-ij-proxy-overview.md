# Agent-native file tools and JetBrains MCP proxy

## File-operation ownership

Claude Code, Codex, and other agent harnesses provide native tools for reading,
editing, writing, patching, and listing local files. ij-proxy does not duplicate
those operations. It remains responsible for IDE-backed search, analysis,
formatting, refactoring, and execution tools.

The Claude Code tool list in `cc-tools.json` is a point-in-time capture from
January 21, 2026 and may differ from current builds.

## Blocked JetBrains MCP tools

JetBrains MCP still exposes file-operation tools that overlap the native agent
surface. ij-proxy filters their current, legacy, and container variants:

- Reads: `read_file`, `get_file_text_by_path`, `container_read_file`.
- Writes and patches: `apply_patch`, `create_new_file`,
  `replace_text_in_file`, `container_write_file`.
- Directory listing: `list_dir`, `list_directory_tree`,
  `container_list_dir`.

These names are rejected for direct `tools/call` requests as well as omitted
from `tools/list`. The filtering can be removed after the overlapping
JetBrains MCP tools are removed upstream.

## Remaining proxy behavior

- Search tools are passed through when the upstream has the current
  `search_*` interface and adapted from legacy tools otherwise.
- Multi-IDE search results are merged; path-scoped analysis, formatting, and
  refactoring calls are routed between IDEA and Rider.
- Container mode routes search through container tools and retains the
  container `bash` adapter. File operations still belong to native agent tools.

## References

- Proxy behavior: `README.md`, `search.md`, `proxy-tools/registry.ts`.
- Claude Code capture: `cc-tools.json`.
- JetBrains MCP server: `community/plugins/mcp-server/src/com/intellij/mcpserver/toolsets/general/`.
