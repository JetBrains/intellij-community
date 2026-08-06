# Workspace Isolation

This repository is too large for agents to create ad hoc Git worktrees or additional clones for workspace isolation.

- Never run `git worktree add`, clone this repository for workspace isolation, or implement another custom workspace-isolation mechanism.
- Never install, initialize, configure, or update Treehouse or another workspace manager automatically.
- When an isolated workspace is required, use the `treehouse` skill. Its Bun CLI checks whether Treehouse is installed and usable for this repository; never bypass it with raw lifecycle commands.
- If Treehouse is available, acquire a durable workspace with the skill's `write acquire` command. Use the current development or agent session ID as the holder when one is available. Acquisition prepares a clean detached workspace at the caller checkout's exact `HEAD`; it does not transfer index, working-tree, or untracked changes.
- An available workspace shown by Treehouse status may have a stale detached `HEAD`; this is expected. Never use, enter, or synchronize a status path directly. Work only from the path returned by `write acquire`.
- The skill records the returned workspace path, lease ID, lease holder, and captured source `HEAD` in an ignored receipt inside the acquired workspace. Work from the returned workspace path and preserve that receipt for the entire session.
- If Treehouse is unavailable or acquisition fails, do not fall back to raw Git worktrees, repository clones, or another workspace manager. Continue in the current checkout when safe, or ask the user to provide an isolated workspace.
- Before returning a workspace, verify that all intended changes are committed or otherwise preserved outside it, that no intended uncommitted or untracked work remains, and that no relevant process is still using it.
- Return the workspace only from outside it with the skill's `write return --workspace <leased-path>` command. The wrapper requires Treehouse to report no live workspace processes, verifies the recorded lease identity, and passes both identity guards to Treehouse. Never use `--force`.
- A workspace with uncommitted changes makes Treehouse ask `Clean and return? [Y/n]`. After the preceding preservation checks pass, run `write return --workspace <leased-path> --confirm-preserved` in an interactive TTY and answer `Y`; a non-interactive invocation is refused. Do not replace the confirmation with `--force`.
- If the workspace cannot be returned safely, retain the lease and report its path, lease ID, and lease holder to the user.
- Never run `treehouse enter`, `treehouse init`, `treehouse update`, `treehouse prune`, `treehouse destroy`, or another command that may enter, alter, or delete another session's workspace.
