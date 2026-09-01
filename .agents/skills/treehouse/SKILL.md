---
name: treehouse
description: Safely acquire, inspect, and return leased Treehouse workspaces.
allowed-tools: Bash(../../../community/tools/treehouse.cmd read:*), Bash(../../../community/tools/treehouse.cmd write:*), Bash(../../../tools/treehouse.cmd read:*), Bash(../../../tools/treehouse.cmd write:*), Bash(./community/tools/treehouse.cmd read:*), Bash(./community/tools/treehouse.cmd write:*), Bash(./tools/treehouse.cmd read:*), Bash(./tools/treehouse.cmd write:*)
---

# Treehouse workspace lifecycle

Use this skill when a task needs an isolated workspace. The CLI wraps only the leased lifecycle
of Treehouse: `read status`, `write acquire`, and `write return`. It prepares the acquired
workspace at the exact `HEAD` of the caller, records the lease identity inside it, and guards
every return with that identity. It never installs or configures Treehouse. It does not expose
`enter`, `init`, `update`, `prune`, `destroy`, or `--force`.

## Run the CLI

Bazel builds the CLI from a pinned source, so the first call in a session can take longer.
Output is JSON. From the repository root:

- Ultimate checkout: `./community/tools/treehouse.cmd …`
- Community checkout: `./tools/treehouse.cmd …`

From this skill directory, use `../../../community/tools/treehouse.cmd …` in an Ultimate checkout
or `../../../tools/treehouse.cmd …` in a Community checkout. Keep a read call and a write call
separate, so an approval stays narrow and reusable.

For Codex, run from this skill directory and request these prefixes when the sandbox asks for an
approval:

- Read-only: `../../../community/tools/treehouse.cmd read`
- Lease mutations: `../../../community/tools/treehouse.cmd write`

The write approval lets the wrapper reach the Treehouse pool outside the repository. It does not
by itself authorize an acquire or a return. Run those only when the task calls for them.

## Inspect the pool

```bash
../../../community/tools/treehouse.cmd read status
```

The result lists every workspace with its lease and its process list. `status` is inspection
only. An available workspace can show an old detached `HEAD`. That is expected. Never enter,
edit, reset, rebase, or synchronize a path taken from `status`. Only `write acquire` reserves
and prepares a workspace.

## Acquire a workspace

For Codex, first check that the built-in `request_permissions` tool is available. If it is not,
do not acquire a lease. Report that Treehouse cannot be used in this session. Do not ask the user
to change permission settings, to restart with `--add-dir`, or to grant access to the Treehouse
pool.

```bash
../../../community/tools/treehouse.cmd write acquire --holder <session-id>
```

Pass the current development or agent session ID as `--holder` when one is available. Without
the option, the CLI uses `TREEHOUSE_LEASE_HOLDER`, then generates an `agent-<UUID>` label. The
result holds the workspace path, the lease ID, the holder, and the receipt path. Keep running
from the original checkout until the permission step below succeeds.

For Codex, a change of the working directory of a tool does not add the leased workspace to the
writable roots of the session. Do this immediately after the acquire and before any edit or
write command inside the workspace. Use `request_permissions` to request filesystem write access
to exactly the returned `path` with session scope. After the grant, use that path as the working
directory for every later tool. Do not request the Treehouse pool, the source checkout, the
shared Git directory, or full access. Do not replace the single workspace grant with repeated
per-command escalations.

If the grant is denied, do not enter, edit, or run commands in the leased workspace. Run
`write return --workspace <leased-path>` from the original checkout at once to return the
untouched lease. If the return fails, retain and report the path, the lease ID, and the holder
as described below. Do not ask the user to reconfigure permissions.

The wrapper refuses an acquire only when the current checkout itself holds a lease. One checkout
can hold several leases from repeated acquires.

The acquire captures the `HEAD` of the caller checkout, takes a clean lease with `--no-fetch`,
and detaches the leased workspace at that exact commit. The index, the working-tree changes, and
the untracked files of the caller are not transferred. The wrapper performs no fetch, rebase,
stash, cherry-pick, or file copy. If the preparation or the verification fails, the wrapper
returns the new lease when that is safe. Otherwise it retains the lease and the receipt, and it
reports their exact identity.

The receipt is `out/treehouse/lease.json` inside the acquired workspace. It has schema version 2
and records the captured `source_head`. `out/` is ignored in both repository layouts. Do not
edit, move, or copy the receipt.

## Return a workspace

Before a return, verify that every intended change is committed or preserved elsewhere. No
intended uncommitted or untracked work may remain. Stop every process that `read status` reports
for the workspace, because the wrapper refuses a return while Treehouse reports one. Then run
from the original checkout, or from another directory outside the leased workspace, and pass
the acquired path:

```bash
../../../community/tools/treehouse.cmd write return --workspace <leased-path>
```

The CLI loads the receipt and checks its path, lease ID, and holder against the live Treehouse
status. It requires an empty process list and checks the Git status. It then calls
`treehouse return` with both identity guards. A run outside the leased workspace keeps the
wrapper and its parent shell out of the process list. The receipt is removed only after
Treehouse reports success.

If Git is dirty after the preservation checks, attest that the work is preserved:

```bash
../../../community/tools/treehouse.cmd write return --workspace <leased-path> --confirm-preserved
```

Treehouse asks `Clean and return? [Y/n]`, and the wrapper answers it. No TTY is required. Pass
the flag only after the preservation checks pass, because the return cleans the workspace. The
wrapper refuses a dirty return without the flag and never substitutes `--force`. It verifies a
return against the live lease state, not against the exit code.

If the return fails, retain the lease and report the path, the lease ID, and the holder from the
error. Do not fall back to a raw Git worktree, another clone, or another workspace manager.

## If Treehouse is unavailable

Exit code 127 with the message `Treehouse is unavailable` means that the CLI could not start.
Bazel builds the CLI from a pinned source, so this is a build failure and not a missing host
install. Do not install Treehouse. Do not fall back to a Git worktree, a clone, or another
workspace manager on your own initiative. Continue in the current checkout when that is safe, or
ask the user for an isolated workspace. Use a Git worktree only when the user explicitly asked
for one for this task.
