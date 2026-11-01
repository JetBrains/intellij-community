# Tools Reference

The repository guide (`AGENTS.md`, section **Tools**) holds the rules. This file holds the recipes and the
inventories that the rules do not need in every session.

The code index is `./community/tools/accio.cmd` in the monorepo, and `./tools/accio.cmd` in a community checkout.
It runs the `accio` on the `PATH` when there is one. Else it runs the pinned build, which it downloads on the
first run. `accio <command> --help` lists the commands.

The text wrappers are `./community/tools/rg.cmd` and `./community/tools/fd.cmd` in the monorepo, and
`./tools/rg.cmd` and `./tools/fd.cmd` in a community checkout. Use them only where accio does not look: a log,
build output, or a path outside the repository.

## accio

Below, `accio` stands for `accio.cmd`. `accio <command> --help` lists the options.

- A task in words, no identifier yet: `accio context '<the question>'`.
- A behaviour or a concept: `accio locate '<a full question>' --mode semantic`.
- A known identifier: `accio symbol Name`, `accio references Name`, `accio trace Name --direction callers`,
  `accio implementations Type`.
- A known string: `accio locate 'text' --scope <dir>/`. A pattern: add `--mode regex`.
- A file by name: `accio files '<name or glob>'`.
- Dead code: `accio unused --scope <dir>/`, with the `duplicate-and-dead-code` skill.
- Each answer carries `quality` (`exact`, `structural`, `heuristic`) and `completeness`. Trust an `exact` and
  `complete` answer as it is.
- accio indexes the files that git tracks, dot-directories such as `.agents/` included.
- A `--scope` that names a git-ignored file or a directory under it is read from the working tree, for example
  `--scope out/air/`. A directory outside the repository is read from disk.
- `--mode regex` has no look-around. Use `rg.cmd --pcre2` for it.

## Text wrappers

- `fd.cmd` and `rg.cmd` skip a dot-directory by default. Agent assets live in `.agents/`, `.claude/`,
  and `.codex/`. Pass `-H` (`--hidden`) to find a skill, a guideline, or a hook. Without it you
  will conclude they do not exist.
- An absolute path through a wrapper is fine.

## Windows and PowerShell

- Do not pass a literal `<`, `>`, `|`, or `&` through a `.cmd` search wrapper, even inside quotes.
- For `rg.cmd` alternation, repeat `-e` (`rg.cmd -n -e "foo" -e "bar" path/to/file.kt`) instead of `"foo|bar"`.
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
