# JetBrains MCP stdio proxy

This is a stdio MCP proxy for JetBrains MCP servers that expose a streamable HTTP endpoint.
It forwards JSON-RPC messages between stdin/stdout and the upstream streamable HTTP server.

## Purpose

- Use JetBrains MCP streamable HTTP servers as stdio MCP servers.
- Hide `project_path`/`projectPath` from tool schemas shown to clients.
- Inject a proxy-controlled project path for `tools/call` requests (defaults to `process.cwd()`), overriding client-provided `project_path`/`projectPath` values (override with `JETBRAINS_MCP_PROJECT_PATH`).

## Multi-IDE support (IDEA + Rider)

In the JetBrains monorepo, `dotnet/` is excluded from IDEA's scope and requires Rider. Neither IDE alone covers all files. When both are running, the proxy discovers both and routes transparently.

- Auto-discover IDEs by scanning ports and matching `serverInfo.name` (e.g. `"JetBrains Rider MCP Server"`). No configuration needed.
- Route file operations (`read_file`, `list_dir`, `get_file_problems`, etc.) to Rider for `dotnet/` paths; everything else goes to IDEA.
- Merge search results (`search_text`, `search_regex`, `search_file`, `search_symbol`) from both IDEs concurrently. Rider results are prefixed with `dotnet/` for monorepo-relative paths.
- Adjust Rider's `project_path` to `dotnet/` and strip `dotnet/` prefixes from file path arguments before forwarding.
- Single-IDE mode: when only one IDE is running, the proxy behaves as a standard single-upstream proxy.

## Usage

Install dependencies (once, from this directory):

```bash
bun install
```

Build the dist bundle (requires bun; run from this directory):

```bash
bun run build
```

Run from dist:

```bash
bun community/build/mcp-servers/ij-proxy/dist/ij-mcp-proxy.mjs
```

Or via bun:

```bash
bun start
```

Environment variables (optional):

- `JETBRAINS_MCP_STREAM_URL` or `MCP_STREAM_URL`: use a specific streamable HTTP endpoint (disables scanning).
- `JETBRAINS_MCP_URL` or `MCP_URL`: alias for `*_STREAM_URL`.
- `JETBRAINS_MCP_PORT_START`: starting port for scan range when no explicit URL is set. Default: `64342`. When not explicitly set, the proxy also probes `64342` and `64344` before scanning.
- `JETBRAINS_MCP_PORT_SCAN_LIMIT`: number of ports to probe in the scan range. Default: `10`.
- `JETBRAINS_MCP_CONNECT_TIMEOUT_S`: timeout for the initial port probe or explicit stream URL (seconds). Default: `10`. Use `0` to disable.
- `JETBRAINS_MCP_SCAN_TIMEOUT_S`: timeout for additional port probes after the default port fails (seconds). Default: `1`. Use `0` to disable.
- `JETBRAINS_MCP_QUEUE_LIMIT`: max number of queued client messages before the stream endpoint is ready. Default: `100`. Use `0` for unlimited.
- `JETBRAINS_MCP_TOOL_CALL_TIMEOUT_S`: timeout for upstream tool calls after they are sent (seconds). Default: `60`. Use `0` to disable.
- `JETBRAINS_MCP_BUILD_TIMEOUT_S`: timeout for `build_project` upstream calls (seconds). Default: `1200` (20 minutes). Use `0` to disable.
- `JETBRAINS_MCP_QUEUE_WAIT_TIMEOUT_S`: timeout for upstream tool calls waiting to be sent while the stream is unavailable (seconds). Defaults to the tool-call timeout when set; use `0` to disable.
- `JETBRAINS_MCP_PROJECT_PATH`: override the injected project path (defaults to `process.cwd()`, relative paths resolve from the current working directory, and `file://` URIs are supported).
- `MCP_LOG`: path to a log file for proxy progress (cleared on startup).
- `JETBRAINS_MCP_PROXY_DISABLE_NEW_SEARCH`: force legacy search tools when available; hides search_text/search_regex/search_file if only the new tools exist.
- `JETBRAINS_MCP_PROXY_DISABLE_WORKAROUNDS`: disable all version-gated workarounds (set to any non-empty value except `0` or `false`).
- `JETBRAINS_MCP_PROXY_DISABLE_WORKAROUND_KEYS`: comma-separated list of workaround keys to disable (see `workarounds.ts`).
- `JETBRAINS_MCP_PROXY_WORKAROUND_DEBUG`: emit debug logs when workarounds are skipped or disabled (set to any non-empty value except `0` or `false`).

## Proxy tool set

The proxy is not a pure pass-through: it exposes a fixed proxy tool set (unless the upstream already provides the same tool name), filters out blocked tools (for example `create_new_file` and `execute_terminal_command`), hides upstream tools that are replaced by proxy tools, and keeps the remaining upstream tools whose names do not collide with proxy tools.

- Proxy tools (when not provided upstream): `read_file`, `list_dir`, `apply_patch`, `rename`.
- Upstream tools: all upstream tools except blocked names, replaced tools, and name collisions.

Notes:
- Upstream JetBrains file tools that are replaced by proxy tools (for example `get_file_text_by_path`, `replace_text_in_file`, `list_directory_tree`) are hidden.
- If the upstream server exposes `read_file` or `search_*`, ij-proxy passes them through unchanged and does not expose proxy shims for those names.
- Search tools and their compatibility are documented in `search.md`.
- Use `apply_patch` to create files.

## Custom tool commands (name + behavior mapping)

The proxy exposes a small, client-shaped tool set. Names are chosen to match Codex CLI conventions (see `/Users/develar/Downloads/codex-main`).

Each proxy command maps to one or more JetBrains MCP tools. Search tool mapping and compatibility are documented in `search.md`.

### Proxy tools

- `read_file`: Matches Codex `read_file` (line-numbered output + indentation mode). Uses `get_file_text_by_path`.
- `list_dir`: Matches Codex `list_dir`. Uses `list_directory_tree`.
- `apply_patch`: Matches Codex `apply_patch` and accepts unified git diff compatibility input (raw or wrapped in `*** Begin Patch` / `*** End Patch`). Uses `get_file_text_by_path` + `create_new_file` and `git rm`/`git mv` for delete/move.
- `apply_patch` unified hunk compatibility: coordinate-only headers like `@@ -1,3 +1,4 @@` are treated as metadata (not search hints). See `apply-patch-unified-hunk-header-spec.md`.
- `rename`: Uses `rename_refactoring`.

Example `.mcp.toml` entry (Codex):

```toml
[mcp_servers.ijproxy]
type = "stdio"
command = "bun"
args = ["community/build/mcp-servers/ij-proxy/dist/ij-mcp-proxy.mjs"]
```

Example `.mcp.json` entry (Claude Code):

```json
{
  "mcpServers": {
    "ijproxy": {
      "type": "stdio",
      "command": "bun",
      "args": [
        "community/build/mcp-servers/ij-proxy/dist/ij-mcp-proxy.mjs"
      ]
    }
  }
}
```

## Tests

From the repo root:

```bash
bun test community/build/mcp-servers/ij-proxy/integration-tests/*.test.ts community/build/mcp-servers/ij-proxy/proxy-tools/handlers/*.test.ts
```

## Notes

- Run from the desired project root so `process.cwd()` matches the injected project path, or set `JETBRAINS_MCP_PROJECT_PATH` (path or `file://` URI) to override it.
- Direct `create_new_file` calls are blocked; use `apply_patch`.
- Requires Bun 1.0+ (Node 18+ if you run the built proxy with node).
