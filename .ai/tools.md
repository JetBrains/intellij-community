# Tools Reference

The repository guide (`AGENTS.md`, section **Tools**) holds the rules. This file holds the recipes and the
inventories that the rules do not need in every session.

The search wrappers are `./community/tools/fd.cmd` and `./community/tools/rg.cmd` in the monorepo, and
`./tools/fd.cmd` and `./tools/rg.cmd` in a community checkout.

## Search wrappers

- `fd.cmd` and `rg.cmd` skip a dot-directory by default. Agent assets live in `.agents/`, `.claude/`,
  `.junie/`, and `.opencode/`. Pass `-H` (`--hidden`) to find a skill, a guideline, or a hook. Without it you
  will conclude they do not exist.
- An absolute path through a wrapper is fine. Pipe into `rg.cmd` instead of `| grep`, because it reads stdin.

## Windows and PowerShell

- Do not pass a literal `<`, `>`, `|`, or `&` through a `.cmd` search wrapper, even inside quotes.
- For `rg.cmd` alternation, repeat `-e` (`rg.cmd -n -e "foo" -e "bar" path/to/file.kt`) instead of `"foo|bar"`.
- To check one file for conflict markers, use
  `Select-String -SimpleMatch -Pattern '<<<<<<<','=======','>>>>>>>' -Path <file>`.

## A spelling the allowlist knows

Prefer a documented wrapper command over a hand-rolled equivalent. A spelling the allowlist knows runs
without a prompt. A novel one does not. Add a new entry to `community/.ai/tool-permissions.json` and rerun
`bazel run @community//.ai:render-guides`. Never edit a harness allowlist by hand.

## Shell outside the working copy

Outside the working copy, shell access is task-scoped. Read what this repo's tooling produced, or what the
user or a skill named: build output, an IDE sandbox (`system/`, `config/`, `idea.log`), a tool cache, or a VM
workspace a skill documents. Do not survey the machine. Do not list or read the home directory,
`~/Downloads`, another checkout, mail, or browser and messaging data. Report a failed step instead of hunting
for an artifact nobody named. If the task needs a path outside that set, ask first.

## IDE-backed semantic tools

Available through ijproxy or JetBrains MCP:

- Inspections and symbol info: `lint_files`, `get_symbol_info`
- Refactors: `rename` (ijproxy) / `rename_refactoring` (JetBrains MCP)
- Formatting: `reformat_file`
- Concurrency checks: `find_threading_requirements_usages`, `find_lock_requirements_usages`
- Project structure and VCS: `get_project_modules`, `get_project_dependencies`, `get_repositories`,
  `git_status`
- Run configs: `get_run_configurations`, `execute_run_configuration`
