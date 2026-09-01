# Workspace Isolation

This repository is too large for agents to create ad hoc Git worktrees or additional clones for workspace isolation.

- When a task needs an isolated workspace, use the [`treehouse` skill](../.agents/skills/treehouse/SKILL.md). It holds the full procedure. Never bypass its wrapper with a raw Treehouse lifecycle command. Never run `treehouse enter`, `init`, `update`, `prune`, `destroy`, or `--force`, because those can enter, alter, or delete another session's workspace.
- Do not run `git worktree add`, clone this repository for workspace isolation, or implement another custom workspace-isolation mechanism on your own initiative. See the explicit-request exception below.
- Never install, initialize, configure, or update Treehouse or another workspace manager automatically.
- If any Treehouse command fails, do not fall back to a Git worktree, a repository clone, or another workspace manager on your own initiative. Continue in the current checkout when safe, or ask the user to provide an isolated workspace.
- If the user explicitly asks for a Git worktree for the current task, create exactly one scoped to that task. Do not ask the user to create it manually.
