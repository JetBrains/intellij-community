# Beads Task MCP (Agent Spec)

## Response envelope
- Every response includes `kind` and `next`.
- Response kinds: `issue`, `summary`, `empty`, `progress`, `created`, `updated`, `closed`, `error`.
- Issue/progress responses may include top-level `memory`.
- Summary responses use `issues` (array of summary issues).
- Summary/issue objects include core fields only.

## Views and memory
Inputs:
- `view="summary" | "meta"` (default `summary`) and `meta_max_chars` (default 400; <=0 disables truncation).
- Applies to: `task_status`, `task_start`, `task_reopen`.

Outputs:
- Summary issue fields: `id`, `title`, `status`, `priority`, `type`, `assignee`, `parent`, `ready_children`, `children`, `is_new` (omit empty).
- `children` is returned for epic issue views (`task_status(id)`) as a list of summary issues.
- Meta fields (only in `view="meta"`): `description`, `design`, `acceptance`.
- `meta_truncated` lists meta fields that were truncated.
- Memory payload is returned when `memory_limit > 0`.
- Shape: `{findings?, decisions?, truncated?, more?}` (omit empty fields).
- `memory_limit` caps each list; `0` omits memory.
- When applying `memory_limit`, return the most recent entries (latest), not the earliest.
- If `truncated` is true, `more` may include counts of omitted items: `{findings, decisions}`.

## Field semantics (WHAT / HOW / WHY)
- `description` = WHAT (scope + user-visible outcome).
- `design` = HOW (approach, architecture, steps, constraints, risks).
- `acceptance` = DONE (verifiable criteria/tests; no implementation steps).
- `findings` = FACTS (observations, repros, measurements, logs).
- `decisions` = WHY (choice + rationale + tradeoffs).

## Input validation
- Tool inputs are strict; unknown fields are rejected.
- Defaults: `view=summary`, `meta_max_chars=400`, `memory_limit=0`.
- `task_create` and `task_decompose` require non-empty `description`, `design`, and `acceptance`.
- `task_link` requires `id` and non-empty `depends_on` (string or string array).
- `task_start` requires `id` or `user_request`.

## Errors (common)
- `task_create`: missing meta -> `Missing required fields: ...`.
- `task_decompose`: sub-issue missing meta -> `sub_issues[i] missing required fields: ...`.
- `task_update_meta`: no fields -> `At least one of description, design, acceptance is required`.
- `task_start`: missing id/user_request -> `task_start requires id or user_request`.

## Tool behaviors (canonical)
- `task_status()` -> `kind: "summary" | "empty" | "error"`
  - When in_progress tasks exist, `summary.issues` lists them all.
  - `empty` means no in_progress tasks.
- `task_status(id, memory_limit?, view?, meta_max_chars?)` -> `kind: "issue" | "error"` (optional `memory`)
- `task_start(user_request, description?, design?, acceptance?, memory_limit?, view?, meta_max_chars?)` -> `kind: "issue" (is_new=true) | "error"`
  - If description/design/acceptance are provided, they are used.
  - Otherwise: description = "USER REQUEST: ...", design/acceptance = "PENDING".
  - Always creates a new epic when `user_request` is provided, even if in_progress tasks exist.
- `task_start(id, memory_limit?, view?, meta_max_chars?)` -> `kind: "issue"` (is_new=false, status `in_progress`)
- `task_progress(..., memory_limit?)` -> `kind: "progress" | "error"` (optional `memory`)
- `task_update_meta(id, description?, design?, acceptance?, view?, meta_max_chars?, memory_limit?)` -> `kind: "issue" | "error"` (optional `memory`)
- `task_decompose(epic_id, sub_issues)` -> `kind: "created" (ids, epic_id, started_child_id) | "error"`
  - Auto-starts when a single child is created.
- `task_create(title, description, design, acceptance, type?, parent?, ...)` -> `kind: "created" (id) | "error"`
  - `type` can be any issue type, including `epic`.
- `task_link(id, depends_on, dep_type?)` -> `kind: "updated" (id, added_depends_on, dep_type?) | "error"`
- `task_done(id, reason)` -> `kind: "closed" (closed, next_ready, epic_status, parent_id)`
  - If the closed issue was the last open child of an epic, the parent epic is auto-closed (>=1 child, not pinned or hooked).
  - Auto-close reason: "Auto-closed: all child issues closed".
- `task_reopen(id, reason, memory_limit?, view?, meta_max_chars?)` -> `kind: "issue" | "error"` (optional `memory`)

## Status and semantics
- Statuses: `open`, `in_progress`, `blocked`, `deferred`, `closed`, `tombstone`, `pinned`, `hooked`.
- Ready queue = open issues with no `blocks` deps; only `blocks` affects readiness.
- Use `blocked` when waiting on a dependency; use `deferred` when intentionally paused.
- `pinned` and `hooked` are protected statuses; do not auto-close.

## Structure
- Priority set on create: `task_create(priority="P2")` (P0..P4 / 0..4).
- Parent/child: `task_decompose(epic_id, ...)` or `task_create(parent=epic_id)`.
- Dependencies: `task_decompose(depends_on=[...])` accepts indices (0..i-1) or issue IDs; `dep_type` on a sub-issue applies the dependency type to all of its `depends_on` entries.
- `task_create(depends_on=..., dep_type=...)` accepts a string or string array and sets a type; add dependencies later with `task_link(id, depends_on, dep_type?)`.
- Epics own child tasks via `parent-child` links.
- Child IDs are dotted (example: `bd-xxxx.1`); up to 3 nesting levels.
- Work happens on child issues; epic is for roll-up only.

## Usage hints
- Start/claim: `task_start(id)` or `task_progress(id, status="in_progress")`.
- Resume without an id: call `task_status()`; if `kind` is `empty`, ask whether to start a new epic; otherwise ask the user which issue to resume.
- After multi-child decomposition, call `task_start(id)` on the chosen child to set `in_progress`.
- Block/defer: `task_progress(id, status="blocked"|"deferred")`.
- Close: `task_done(id, reason)`. Reopen: `task_reopen(id, reason)`.
- `task_status(id=epic)` returns child statuses.
- `task_done` returns `next_ready` and `epic_status`.
- `task_start(user_request)` always creates a new epic; call `task_status()` first if you need to review in_progress work.
- Use `task_update_meta` for description/design/acceptance changes; use `task_progress` for findings/decisions/status updates.

## Examples (one-liners)
- `task_status()`
- `task_status(id="bd-123", view="meta")`
- `task_start(user_request="Add caching")`
- `task_start(user_request="Add caching", description="Add cache layer", design="LRU in front of DB", acceptance="Cache hits/misses tracked")`
- `task_start(id="bd-123")`
- `task_progress(id="bd-123", decisions=["Use LRU cache"])`
- `task_update_meta(id="bd-123", design="LRU in front of DB")`
- `task_decompose(epic_id="bd-123", sub_issues=[{title:"Add cache", description:"Add cache layer", design:"LRU map", acceptance:"Cache hit/miss verified"}])`
- `task_decompose(epic_id="bd-123", sub_issues=[{title:"Wire cache", description:"Hook cache", design:"Call cache API", acceptance:"Cache used", depends_on:["bd-122"], dep_type:"blocks"}])`
- `task_create(title="Add cache", description="Add cache layer", design="LRU map", acceptance="Cache hit/miss verified")`
- `task_create(title="Wire cache", description="Hook cache", design="Call cache API", acceptance="Cache used", depends_on=["bd-101","bd-102"])`
- `task_link(id="bd-103", depends_on=["bd-101","bd-102"])`
- `task_done(id="bd-123", reason="Completed")`
- `task_reopen(id="bd-123", reason="Regression found")`

## Decomposition guidelines
- Prefer 2-7 children per epic; avoid single-child decompositions unless justified.
- Each child should be doable in ~0.5-2 hours; split if larger.
- Do not split by phase (design/impl/test); split by deliverable behavior.
- Use dependencies only when truly blocking; avoid chains that serialize work.
