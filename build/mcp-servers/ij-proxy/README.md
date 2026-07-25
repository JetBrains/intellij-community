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
- Route path-scoped analysis, formatting, and refactoring operations to Rider for `dotnet/` paths; everything else goes to IDEA.
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
- `JETBRAINS_MCP_BUILD_TIMEOUT_S`: timeout for long-running upstream calls such as `build_project` and `lint_files` (seconds). Default: `1200` (20 minutes). Use `0` to disable.
- `JETBRAINS_MCP_QUEUE_WAIT_TIMEOUT_S`: timeout for upstream tool calls waiting to be sent while the stream is unavailable (seconds). Defaults to the tool-call timeout when set; use `0` to disable.
- `JETBRAINS_MCP_PROJECT_PATH`: override the injected project path (defaults to `process.cwd()`, relative paths resolve from the current working directory, and `file://` URIs are supported).
- `MCP_LOG`: path to a log file for proxy progress (cleared on startup).

## Supported IDE versions

ij-proxy targets IntelliJ platform build **262 and newer**. That generation ships `search_text`,
`search_regex`, `search_file`, `search_symbol`, `lint_files(files)` and `reformat_file(files)`, so the
proxy forwards them unchanged instead of emulating older tool shapes.

## Proxy tool set

The proxy is not a pure pass-through: it exposes a fixed proxy tool set (unless the upstream already provides the same tool name), filters out blocked tools, hides upstream tools that are replaced by proxy tools, and keeps the remaining upstream tools whose names do not collide with proxy tools.

- Proxy tools: `rename`; container sessions additionally expose `bash` and container-routed `search_text` / `search_regex` / `search_file`.
- Upstream tools: all upstream tools except blocked names, replaced tools, and name collisions.

Notes:
- File reads, writes, patches, and directory listings use the agent harness's native tools, not MCP.
- JetBrains MCP file-operation tools, including the container variants, are blocked until they are removed upstream.
- `search_*`, `lint_files` and `reformat_file` are upstream tools passed through unchanged; the proxy only normalizes their arguments and, in a dual-IDE setup, splits and merges them across IDEA and Rider.
- `get_file_problems` is hidden: it is the per-file variant of `lint_files`, and exposing both invites the agent to lint one file at a time.
- `lint_files` responses may include file entries with `timedOut: true` and empty `problems`; top-level `more: true` still means the overall batch is incomplete.
- Search tools are documented in `search.md`.

## Custom tool commands (name + behavior mapping)

The proxy exposes a small, client-shaped search and IDE tool set. File operations stay with the agent harness.

### Proxy tools

- `rename`: Uses `rename_refactoring`.
- `search_text`, `search_regex`, `search_file` (container sessions only): Route the search into the container instead of the host project. See `search.md`.
- `bash` (container sessions only): Runs a shell command inside the container via `container_exec`.

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
- JetBrains MCP file-operation calls are blocked; use the agent harness's native file tools.
- Requires Bun 1.0+ (Node 18+ if you run the built proxy with node).
