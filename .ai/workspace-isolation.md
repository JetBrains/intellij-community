# Workspace Isolation

This repository is too large for agents to create ad hoc Git worktrees or additional clones for workspace isolation.

- Never run `git worktree add`, clone this repository for workspace isolation, or implement another custom workspace-isolation mechanism.
- Never install, initialize, configure, or update Treehouse or another workspace manager automatically.
- When an isolated workspace is required, first check whether `treehouse` is already installed and usable for this repository.
- If Treehouse is available, acquire a durable workspace using `treehouse get --lease --json --lease-holder <unique-session-id>`. Use the current development or agent session ID as the holder; if none is available, use another unique, stable label for the session.
- Record the returned workspace path, lease ID, and lease holder for the entire session, and work from the returned workspace path.
- If Treehouse is unavailable or acquisition fails, do not fall back to raw Git worktrees, repository clones, or another workspace manager. Continue in the current checkout when safe, or ask the user to provide an isolated workspace.
- Before returning a workspace, verify that all intended changes are committed or otherwise preserved outside it, that no intended uncommitted or untracked work remains, and that no relevant process is still using it.
- Return the workspace only with the recorded lease identity: `treehouse return <path> --if-lease-id <lease-id> --if-lease-holder <lease-holder>`. Never use `--force`.
- If the workspace cannot be returned safely, retain the lease and report its path, lease ID, and lease holder to the user.
- Never run `treehouse enter`, `treehouse init`, `treehouse update`, `treehouse prune`, `treehouse destroy`, or another command that may enter, alter, or delete another session's workspace.
