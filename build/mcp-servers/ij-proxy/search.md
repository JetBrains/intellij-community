# ij-proxy Search: API Shape

This document describes the search tool surface clients see and the two modes ij-proxy runs in.

ij-proxy supports IntelliJ platform build **262 and newer**. That generation always exposes
`search_text`, `search_regex`, `search_file` and `search_symbol`, so there is no emulation layer for
older tool shapes.

## Host mode (default): upstream passthrough

The IDE's own search tools are forwarded unchanged — same names, same schemas, same output. ij-proxy
does not define the parameters, does not filter results, and does not apply its own path matching.
Consult the IDE's tool descriptions (or `tools/list`) for the authoritative argument list.

Two things still happen in the proxy:

- **Blocking and hiding.** File-operation tools are blocked so file reads/writes stay with the agent
  harness; see `README.md` for the list.
- **Dual-IDE merging.** With both IDEA and Rider connected, a search call fans out to both and the
  results are merged, with Rider paths prefixed by `dotnet/`. See `routing.ts`.

`search_symbol` is passed through like the rest — ij-proxy never emulates symbol search.

## Container mode: routed into the container

When a container session is active (`.container-sessions.jsonl`), `search_text`, `search_regex` and
`search_file` are replaced by proxy tools that call the IDE's `container_search_*` tools instead, so
the search runs inside the container rather than over the host project. Handlers live in
`proxy-tools/container-handlers.ts`.

Arguments the container handlers read:

- `search_text(q, searchPath?, limit?)` — `q` is a literal substring; also accepts `query`.
- `search_regex(pattern, searchPath?, limit?)` — `pattern` is a regular expression; also accepts `q`.
- `search_file(pattern, searchPath?, limit?)` — `pattern` is a glob; also accepts `glob`.

`searchPath` (or `path`) is resolved to a container-absolute path under the session workspace, and
defaults to the workspace root. Absolute host paths outside the workspace or project are rejected
rather than silently remapped. Output is plain text, tagged with `[container:<sessionId>]`.

`search_symbol` is not routed into the container — it keeps using the host IDE index, since the
container has no index.

Note: the container search schemas in `proxy-tools/schemas.ts` still advertise a `paths` argument the
handlers ignore. Pre-existing; trimming it would change an agent-visible contract.

## How ij-proxy and JetBrains MCP Complement Each Other

JetBrains MCP is the source of truth for indexing and search inside the IDE. ij-proxy does not
implement its own index. On the host it only routes; the container handlers exist because a container
workspace is not in the IDE index at all.
