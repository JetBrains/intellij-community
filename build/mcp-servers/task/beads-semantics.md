# Beads Issue Tracker (Task MCP)

## Task MCP response shape
- Every response includes `kind` and `next`.
- Interactive prompts use `kind: "need_user"` with `question` and `choices`.
- Each choice includes `action` plus optional `id`/`parent`.
- Issue/progress responses may include top-level `memory`.
- `issue` objects include core fields only; `notes`/`comments` are never returned (even in meta view).

## Issue views
- Inputs: `view="summary" | "meta"` (default `summary`) and `meta_max_chars` (per-field truncation; default 400; <=0 disables truncation).
- Applies to: `task_status`, `task_start`, `task_reopen`.
- Summary fields: `id`, `title`, `status`, `priority`, `type`, `assignee`, `parent`, `ready_children`, `is_new` (omit empty).
- Meta fields (only in `view="meta"`): `description`, `design`, `acceptance`.
- `meta_truncated` lists meta fields that were truncated.

## Memory payload (optional)
- Returned as `memory` when the caller passes `memory_limit > 0`.
- Shape: `{findings?, decisions?, truncated?, more?}` (omit empty fields).
- `memory_limit` caps each list; `0` omits memory.
- When applying `memory_limit`, return the most recent entries (latest), not the earliest.
- If `truncated` is true, `more` may include counts of omitted items: `{findings, decisions}`.

## Tool outputs (canonical)
- `task_status()` -> `kind: "need_user" | "summary" | "empty" | "error"`
- `task_status(id, memory_limit?, view?, meta_max_chars?)` -> `kind: "issue" | "error"` (optional `memory`)
- `task_start(user_request, memory_limit?, view?, meta_max_chars?)` -> `kind: "issue" (is_new=true) | "need_user" | "empty"`
- `task_start(id, memory_limit?, view?, meta_max_chars?)` -> `kind: "issue"` (is_new=false, status `in_progress`)
- `task_progress(..., memory_limit?)` -> `kind: "progress" | "need_user" | "error"` (optional `memory`)
- `task_decompose(epic_id, sub_issues)` -> `kind: "created" (ids, epic_id, started_child_id) | "error"`
- `task_create(title, ...)` -> `kind: "created" (id) | "error"`
- `task_done(id, summary)` -> `kind: "need_user" (confirm_close) | "closed" (closed, next_ready, epic_status, parent_id)`
- `task_done(id, confirmed=true)` -> `kind: "closed" (closed, next_ready, epic_status, parent_id)`
- `task_reopen(id, reason, memory_limit?, view?, meta_max_chars?)` -> `kind: "issue" | "error"` (optional `memory`)

## Status updates via Task MCP
- Start/claim: `task_start(id)` or `task_progress(id, status="in_progress")`
- Block/defer: `task_progress(id, status="blocked"|"deferred")`
- Close: `task_done(id, summary)` (may require confirm flow)
- Reopen: `task_reopen(id, reason)`

## Structure via Task MCP
- Priority set on create: `task_create(priority="P2")` (P0..P4 / 0..4)
- Parent/child: `task_decompose(epic_id, ...)` or `task_create(parent=epic_id)`
- Dependencies: `task_decompose(depends_on=[...])` uses default dep type; `task_create(depends_on=..., dep_type=...)` sets a type

## Beads semantics
- Statuses: `open`, `in_progress`, `blocked`, `deferred`, `closed`, `tombstone`, `pinned`
- Ready queue = open issues with no `blocks` deps; only `blocks` affects readiness
- Use `blocked` when waiting on a dependency; use `deferred` when intentionally paused
- `pinned` reserved for orchestrators; do not auto-close

## Dependencies
- Types: `blocks` (hard), `related` (soft), `parent-child`, `discovered-from`
- Only `blocks` affects the ready queue

## Parent/child (epics)
- Epics own child tasks via `parent-child` links
- Child IDs are dotted (example: `bd-xxxx.1`); up to 3 nesting levels
- Work happens on child issues; epic is for roll-up only

## Issue fields
- `type` (renamed from `issue_type`) identifies task vs epic.
- `description`, `design`, `acceptance` (renamed from `acceptance_criteria`) are only in `view="meta"`.
- Task MCP exposes findings/decisions via `memory`, not `issue.notes`.

## Task MCP hints
- `task_status(id=epic)` returns child statuses
- `task_done` returns `next_ready` and `epic_status`

## Decomposition guidelines (task_decompose)
- Decompose into the smallest independently testable units with clear acceptance
- Prefer 2–7 children per epic; avoid single-child decompositions unless justified
- Each child should be doable in ~0.5–2 hours; split if larger
- Do not split by phase (design/impl/test); split by deliverable behavior
- Use dependencies only when truly blocking; avoid chains that serialize work
