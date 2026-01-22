# JetBrains MCP stdio proxy

This is a stdio MCP proxy for JetBrains MCP servers that expose a streamable HTTP endpoint.
It forwards JSON-RPC messages between stdin/stdout and the upstream streamable HTTP server.

## Purpose

- Use JetBrains MCP streamable HTTP servers as stdio MCP servers.
- Hide `project_path`/`projectPath` from tool schemas shown to clients.
- Inject the current working directory (`process.cwd()`) as the project path for `tools/call` requests.

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
- `JETBRAINS_MCP_QUEUE_WAIT_TIMEOUT_S`: timeout for upstream tool calls waiting to be sent while the stream is unavailable (seconds). Defaults to the tool-call timeout when set; use `0` to disable.
- `MCP_LOG`: path to a log file for proxy progress (cleared on startup).
- `JETBRAINS_MCP_TOOL_MODE`: tool API shape to expose. `codex` (default) uses `read_file`, `grep`, `find`, `list_dir`, `apply_patch`. `cc` uses `read`, `edit`, `write`, `glob`, `grep`.

## Tool variants

The proxy is not a pure pass-through: it always exposes a mode-specific proxy tool set, filters out blocked tools (for example `create_new_file` and `execute_terminal_command`), hides upstream tools that are replaced by proxy tools, and keeps the remaining upstream tools whose names do not collide with proxy tools.

| Mode  | Proxy tools (always exposed)                           | Upstream tools also exposed                                                   |
|-------|--------------------------------------------------------|-------------------------------------------------------------------------------|
| codex | `read_file`, `grep`, `find`, `list_dir`, `apply_patch` | All upstream tools except blocked names, replaced tools, and name collisions. |
| cc    | `read`, `write`, `edit`, `glob`, `grep`                | All upstream tools except blocked names, replaced tools, and name collisions. |

Notes:
- Upstream JetBrains file tools that are replaced by proxy tools (for example `get_file_text_by_path`, `replace_text_in_file`, `find_files_by_name_keyword`, `find_files_by_glob`, `search_in_files_*`, `list_directory_tree`) are hidden.
- Use `apply_patch` (codex) or `write` (cc) to create files.

## Custom tool commands (name + behavior mapping)

The proxy exposes a small, client-shaped tool set. Names are chosen to match client conventions:

- **codex** mode mirrors the Codex CLI tool surface (see `/Users/develar/Downloads/codex-main`).
- **cc** mode mirrors the Claude Code tool surface (see `cc-tools.json`).

Each proxy command maps to one or more JetBrains MCP tools:

### codex mode

| Proxy command | Why this name | JetBrains MCP under the hood |
|---|---|---|
| `read_file` | Matches Codex `read_file` (line-numbered output + indentation mode). | `get_file_text_by_path` |
| `grep` | Codex-style content search. The closest Codex upstream tool is `grep_files`; we expose `grep` with Codex-like args. | `search_in_files_by_regex` (plus `find_files_by_glob` when probing file paths) |
| `find` | Convenience file-discovery command used by Codex flows (not present in Codex upstream). | `find_files_by_glob` or `find_files_by_name_keyword` |
| `list_dir` | Matches Codex `list_dir`. | `list_directory_tree` |
| `apply_patch` | Matches Codex `apply_patch`. | `get_file_text_by_path` + `create_new_file`; uses `git rm`/`git mv` for delete/move |

### cc mode

| Proxy command | Why this name | JetBrains MCP under the hood |
|---|---|---|
| `read` | Matches Claude Code `read` (raw text). | `get_file_text_by_path` |
| `write` | Matches Claude Code `write`. | `create_new_file` (overwrite) |
| `edit` | Matches Claude Code `edit`. | `get_file_text_by_path` + `create_new_file` |
| `glob` | Matches Claude Code `glob`. | `find_files_by_glob` |
| `grep` | Matches Claude Code `grep`. | `search_in_files_by_regex` |

Example `.mcp.toml` entry (Codex):

```toml
[mcp_servers.ijproxy]
type = "stdio"
command = "bun"
args = ["community/build/mcp-servers/ij-proxy/dist/ij-mcp-proxy.mjs"]
# Optional: tool mode is "codex" by default
# env = { JETBRAINS_MCP_TOOL_MODE = "codex" }
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
      ],
      "env": {
        "JETBRAINS_MCP_TOOL_MODE": "cc"
      }
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

- Run from the desired project root so `process.cwd()` matches the project path.
- Direct `create_new_file` calls are blocked; use `apply_patch` (codex) or `write` (cc).
- Requires Bun 1.0+ (Node 18+ if you run the built proxy with node).
