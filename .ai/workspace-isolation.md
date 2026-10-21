# Workspace Isolation

This repository is too large for agents to create ad hoc Git worktrees or additional clones for workspace isolation.

- When a task needs an isolated workspace, lease one with `bd lease` of [Branch Deck](https://github.com/develar/branch-deck), as the sections below describe. Every developer installs Branch Deck. This repository pins no copy of it.
- Do not run `git worktree add`, clone this repository for workspace isolation, or implement another custom workspace-isolation mechanism on your own initiative. See the explicit-request exception below.
- Never install, initialize, configure, or update Branch Deck, Treehouse, or another workspace manager automatically.
- Never run the Treehouse CLI, because it can enter, alter, or delete another session's workspace. A workspace leased earlier from Treehouse (`~/.treehouse/`) goes back by the user with `treehouse return`.
- If the user explicitly asks for a Git worktree for the current task, create exactly one scoped to that task. Do not ask the user to create it manually.

## Check the CLI first

`bd --version` prints JSON with `version`. It must be 0.22.0 or newer. When the command fails or the version is
older, do not lease. Continue in the current checkout when that is safe, or ask the user for an isolated workspace.

## The lifecycle

```bash
bd lease get --holder <session-id>
bd pick --from <leased-path> --follow
bd lease return --workspace <leased-path>
```

- `get` runs from the repository root. Its JSON gives `path` and `lease_id`. The workspace is detached at the exact
  `HEAD` of the caller, and the `.worktreeinclude` files are copied into it. Work and commit only inside `path`.
- `pick` runs in the original checkout. It lands the commits of the workspace on the branch of that checkout.
- `return` runs outside the workspace. It refuses local changes, commits that the source branch does not hold by
  content, and a process inside. It stops the Bazel servers of the workspace itself.
- `bd lease status` reads only the pool state. The path that it reports is for inspection, not for work.

Before the first `get`, read the `branch-deck` skill that `bd install` puts in place, or `bd help worktrees`. It
holds the refusals, `--confirm-preserved`, and the clean rules of `.branchdeck.toml`. This document does not repeat them.

## When a command fails

`bd` writes a JSON error with `code` and `message`. Never fall back to a Git worktree, a clone, or another workspace
manager on your own initiative. Continue in the current checkout when that is safe, or ask the user for an isolated
workspace. When a lease survives the failure, keep it and report its `path`, `lease_id` and holder.

## Codex sandbox

1. Before a `get`, check that the built-in `request_permissions` tool is available. If it is not, do not lease, and
   report that a workspace cannot be leased in this session. Do not ask the user to change permission settings.
2. A change of the working directory of a tool does not add the workspace to the writable roots of the session. Use
   `request_permissions` for session-scoped write access to exactly the returned `path`, before any edit inside it.
   Do not request the pool, the source checkout, the shared Git directory, or full access.
3. If the grant is denied, do not enter, edit, or run a command in the workspace. Return the untouched lease at once
   from the original checkout.
