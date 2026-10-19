# Workspace Isolation

This repository is too large for agents to create ad hoc Git worktrees or additional clones for workspace isolation.

- When a task needs an isolated workspace, use the [`workspace-lease` skill](../.agents/skills/workspace-lease/SKILL.md). It holds the full procedure: `bd lease` of Branch Deck, which every developer installs. Never run the Treehouse CLI, because it can enter, alter, or delete another session's workspace.
- Do not run `git worktree add`, clone this repository for workspace isolation, or implement another custom workspace-isolation mechanism on your own initiative. See the explicit-request exception below.
- Never install, initialize, configure, or update Branch Deck, Treehouse, or another workspace manager automatically.
- If `bd` is missing or any `bd lease` command fails, do not fall back to a Git worktree, a repository clone, or another workspace manager on your own initiative. Continue in the current checkout when safe, or ask the user to provide an isolated workspace.
- If the user explicitly asks for a Git worktree for the current task, create exactly one scoped to that task. Do not ask the user to create it manually.
