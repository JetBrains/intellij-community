# Tools Reference

The repository guide (`AGENTS.md`, section **Tools**) holds the rules. This file holds the recipes and the
inventories that the rules do not need in every session.

The code index is `./community/tools/accio.cmd` in the monorepo, and `./tools/accio.cmd` in a community checkout.
It runs the `accio` on the `PATH` when there is one. Else it runs the pinned build, which it downloads on the
first run. The `accio` skill holds its commands and the rules to read an answer.

The text wrappers are `./community/tools/rg.cmd` and `./community/tools/fd.cmd` in the monorepo, and
`./tools/rg.cmd` and `./tools/fd.cmd` in a community checkout. Use them only where accio does not look: a log,
build output, or a path outside the repository.

## Text wrappers

- `fd.cmd` and `rg.cmd` skip a dot-directory by default. Agent assets live in `.agents/`, `.claude/`,
  and `.codex/`. Pass `--hidden` to find a skill, a guideline, or a hook. Without it you
  will conclude they do not exist. `-H` is the short form for `fd.cmd` only: for `rg.cmd` it is another flag.
- An absolute path through a wrapper is fine.

## Windows and PowerShell

- Do not pass a literal `<`, `>`, `|`, or `&` through a `.cmd` search wrapper, even inside quotes.
- For `rg.cmd` alternation, repeat `-e` (`rg.cmd -n -e "foo" -e "bar" path/to/file.kt`) instead of `"foo|bar"`.
- Git Bash converts an argument that starts with `/` to a Windows path. Pass such text on stdin.
- Git Bash cuts `//` to `/` in a label of a one-segment package, such as `//.ai:render-guides`. Prefix the command
  with `MSYS_NO_PATHCONV=1`.
- To check one file for conflict markers, use
  `Select-String -SimpleMatch -Pattern '<<<<<<<','=======','>>>>>>>' -Path <file>`.

## A spelling the allowlist knows

Prefer a documented wrapper command over a hand-rolled equivalent. A spelling the allowlist knows runs
without a prompt. A novel one does not. Add a new entry to `community/.ai/tool-permissions.json` and rerun
`./bazel.cmd run //.ai:render-guides`. Never edit a harness allowlist by hand.

## Shell outside the working copy

Outside the working copy, shell access is task-scoped. Read what this repo's tooling produced, or what the
user or a skill named: build output, an IDE sandbox (`system/`, `config/`, `idea.log`), a tool cache, or a VM
workspace a skill documents. Do not survey the machine. Do not list or read the home directory,
`~/Downloads`, another checkout, mail, or browser and messaging data. Report a failed step instead of hunting
for an artifact nobody named. If the task needs a path outside that set, ask first.

## IDE-backed semantic tools

Available through JetBrains MCP:

- Inspections and symbol info: `lint_files`, `get_symbol_info`
- Refactors: `rename_refactoring`
- Formatting: `reformat_file`
- Concurrency checks: `find_threading_requirements_usages`, `find_lock_requirements_usages`
- Project structure and VCS: `get_project_modules`, `get_project_dependencies`, `get_repositories`,
  `git_status`
- Run configs: `get_run_configurations`, `execute_run_configuration`
