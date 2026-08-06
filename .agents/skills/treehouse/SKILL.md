---
name: treehouse
description: Safely acquire, inspect, and return leased Treehouse workspaces.
allowed-tools: Bash(../../../community/tools/bun.cmd ./scripts/treehouse.ts read:*), Bash(../../../community/tools/bun.cmd ./scripts/treehouse.ts write:*), Bash(../../../tools/bun.cmd ./scripts/treehouse.ts read:*), Bash(../../../tools/bun.cmd ./scripts/treehouse.ts write:*), Bash(./community/tools/bun.cmd ./.claude/skills/treehouse/scripts/treehouse.ts read:*), Bash(./community/tools/bun.cmd ./.claude/skills/treehouse/scripts/treehouse.ts write:*), Bash(./tools/bun.cmd ./.claude/skills/treehouse/scripts/treehouse.ts read:*), Bash(./tools/bun.cmd ./.claude/skills/treehouse/scripts/treehouse.ts write:*)
---

# Treehouse workspace lifecycle

Use this skill when an isolated workspace is required. The CLI wraps only Treehouse's safe leased
lifecycle, prepares the acquired workspace at the caller's exact `HEAD`, records the lease identity
inside it, and always guards return with that identity. It never installs or configures Treehouse
and does not expose `enter`, `init`, `update`, `prune`, `destroy`, or `--force`.

Run `./scripts/treehouse.ts` through the repository-pinned Bun wrapper. From the repository root:

- Ultimate checkout: `./community/tools/bun.cmd ./.claude/skills/treehouse/scripts/treehouse.ts …`
- Community checkout: `./tools/bun.cmd ./.claude/skills/treehouse/scripts/treehouse.ts …`

From this skill directory, use `../../../community/tools/bun.cmd ./scripts/treehouse.ts …` in an
Ultimate checkout or `../../../tools/bun.cmd ./scripts/treehouse.ts …` in a Community checkout.
Output is JSON. Keep read and write invocations separate so approvals remain narrow and reusable.

For Codex, run from this skill directory and request these narrow reusable prefixes when sandbox
access requires approval:

- Read-only: `../../../community/tools/bun.cmd ./scripts/treehouse.ts read`
- Lease mutations: `../../../community/tools/bun.cmd ./scripts/treehouse.ts write`

The write approval permits the wrapper to access Treehouse's pool outside the repository. It does
not authorize acquiring or returning a workspace unless the task calls for that lifecycle action.

## Inspect the pool

```bash
../../../community/tools/bun.cmd ./scripts/treehouse.ts read status
```

The result includes Treehouse's process list for every workspace. Before returning a workspace,
stop every process still using it; the wrapper refuses to return a workspace while Treehouse
reports any process.

`status` is inspection only. An available reusable worktree may have an old detached `HEAD`; that
is expected. Never enter, edit, reset, rebase, or otherwise synchronize a path taken from status.
Only `write acquire` reserves and prepares a workspace for use.

## Acquire a workspace

```bash
../../../community/tools/bun.cmd ./scripts/treehouse.ts write acquire --holder <session-id>
```

Use the current development or agent session ID as `--holder` when one is available. If it is
omitted, the CLI uses `TREEHOUSE_LEASE_HOLDER`, then generates a unique `agent-<UUID>` label. The
result contains the workspace path, lease ID, holder, and receipt path. Change the working directory
to the returned workspace path and keep all subsequent work there.

Acquisition captures the caller checkout's `HEAD`, obtains a clean lease, and detaches the leased
workspace at that exact commit. The caller's index, working-tree changes, and untracked files are
intentionally ignored and are not transferred. The wrapper performs no fetch, rebase, stash,
cherry-pick, or file copying. If preparation or verification fails, it returns the new lease when
safe; otherwise it retains the lease and receipt and reports their exact identity.

The version-2 receipt is `out/treehouse/lease.json` inside the acquired workspace and records the
captured `source_head`. `out/` is ignored in both repository layouts. Do not edit, move, or copy the
receipt between workspaces. Version-1 receipts from existing leases remain returnable.

## Return a workspace

Before return, verify that all intended changes are committed or preserved elsewhere and no
intended uncommitted or untracked work remains. Stop every process reported for the workspace by
`read status`. Then run from the original checkout or another directory outside the leased
workspace, passing the acquired path explicitly:

```bash
../../../community/tools/bun.cmd ./scripts/treehouse.ts write return --workspace <leased-path>
```

The CLI loads the local receipt, verifies its path, lease ID, and holder against live Treehouse
status, requires an empty process list, checks Git status, and calls `treehouse return` with both
identity guards. Running outside the leased workspace prevents the wrapper and its parent shell
from appearing as workspace processes. The receipt is removed only after Treehouse reports success.

If Git is dirty after the preservation checks, use an interactive TTY and explicitly attest that
the work is preserved:

```bash
../../../community/tools/bun.cmd ./scripts/treehouse.ts write return --workspace <leased-path> --confirm-preserved
```

Treehouse will ask `Clean and return? [Y/n]`; answer `Y` only after those checks pass. The wrapper
refuses a dirty return without the flag or without a TTY, and never substitutes `--force`.

If return fails, retain the lease and report the path, lease ID, and holder printed in the error.
Do not fall back to a raw Git worktree, another clone, or another workspace manager.
