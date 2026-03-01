{{PARTIAL:frontmatter}}

<!-- TEMPLATE:COMMENT -->
To regenerate, run `node community/.ai/render-guides.mjs`.
<!-- /TEMPLATE:COMMENT -->

**Critical:** These guidelines MUST be followed at all times.

## Project Invariants

- The repository is a large monorepo with multiple IDE products and plugins.
- Module/plugin directories may contain their own AGENTS/CLAUDE instructions; follow them when present.
- `*.iml` files are the source of truth and auto-generate `BUILD.bazel` files.
- User-visible strings belong in `*.properties` for localization.

{{PARTIAL:module-specific}}

## Mandatory Rules

### After Code Changes

- **Run affected tests:** `./tests.cmd -Dintellij.build.test.patterns=<FQN or wildcard>` (**FQN required; simple class names do not match**), or `node --test <file>` for `*.test.mjs`.
  `tests.cmd` performs Bazel compilation internally, so a separate `bazel build` step is not needed when tests will be run.
  Module-specific rules may override the runner. Skip if plugin has no tests. See [TESTING](../.agents/skills/testing/SKILL.md).
- **Bazel compilation without tests:** when only verifying compilation (no tests to run), use `bazel build <target>` for affected modules. Skip if only `.js`, `.mjs`, `.md`, `.txt`, or `.json` files are modified.
- After modifying `*.iml`, `BUILD.bazel`, or `.idea/` files: run `./build/jpsModelToBazel.cmd`.

### After Writing Code

- Use `get_file_problems` with `errorsOnly=false` to check files for warnings.
  Fix any warnings related to the code changes made. You may ignore unrelated warnings.

## Repository-wide rules

Preserve IDE-serialized .iml files in canonical form. Do not:

- add comments
- auto-format
- normalize (structure or whitespace)
- prune (remove) empty tags
- reorder elements or attributes

## Tools (use in this order)

### ijproxy (required when available)
<!-- IF_TOOL:CODEX -->
- Read: `mcp__ijproxy__read_file`
- Edit/Write: `mcp__ijproxy__apply_patch`
- **Search symbols (preferred):** `mcp__ijproxy__search_symbol`
- Find files (glob): `mcp__ijproxy__search_file`
- Search text: `mcp__ijproxy__search_text`
- Search regex: `mcp__ijproxy__search_regex`
- List dir: `mcp__ijproxy__list_dir`
<!-- /IF_TOOL:CODEX -->
<!-- IF_TOOL:CLAUDE -->
- Read: `read`
- Edit: `edit`
- Write: `write`
- **Search symbols (preferred):** `search_symbol`
- Find files (glob): `search_file`
- Search text: `search_text`
- Search regex: `search_regex`
- List dir: `list_dir`
<!-- /IF_TOOL:CLAUDE -->

### jetbrains MCP (fallback)
Direct JetBrains MCP connection. Use when ijproxy unavailable.

- Read: `get_file_text_by_path`
- Edit: `replace_text_in_file`
- Write: `create_new_file`
- Find by glob: `find_files_by_glob`
- Find by name: `find_files_by_name_keyword`
- Search text: `search_in_files_by_text`
- Search regex: `search_in_files_by_regex`
- List dir: `list_directory_tree`

### Client fallback (no MCP)
<!-- IF_EDITION:ULTIMATE -->
- **No MCP:** use `./community/tools/fd.cmd` (file search) and `./community/tools/rg.cmd` (text/regex search). These are the only allowed shell file ops on repo paths.
<!-- /IF_EDITION:ULTIMATE -->
<!-- IF_EDITION:COMMUNITY -->
- **No MCP:** use `./tools/fd.cmd` (file search) and `./tools/rg.cmd` (text/regex search). These are the only allowed shell file ops on repo paths.
<!-- /IF_EDITION:COMMUNITY -->

### IDE-backed semantic tools
Available via ijproxy or JetBrains MCP. Use these for semantic operations; avoid manual search/replace when a refactor exists.

- **Default to `search_symbol` (if available) for classes/methods/fields; use `search_text`/`search_regex` mainly for strings, comments, and non-symbol matches.**
- Inspections & symbol info: `get_file_problems`, `get_symbol_info`
- Refactors: `rename` (ijproxy) / `rename_refactoring` (JetBrains MCP); use for renames and avoid manual search/replace.
- Formatting: `reformat_file`
- Concurrency checks: `find_threading_requirements_usages`, `find_lock_requirements_usages`
- Project structure & VCS: `get_project_modules`, `get_project_dependencies`, `get_repositories`, `git_status`
- Run configs: `get_run_configurations`, `execute_run_configuration`

### Tooling rules
- When ijproxy MCP is available, all repo file ops (read/search/edit/write) MUST use ijproxy tools. Do not use JetBrains MCP or generic tools.
- Fallback tools (JetBrains MCP / client) are allowed only when ijproxy is unavailable.
<!-- IF_TOOL:CODEX -->
- For repo edits, use `mcp__ijproxy__apply_patch`. Generic `apply_patch` is forbidden unless ijproxy is unavailable.
<!-- /IF_TOOL:CODEX -->
<!-- IF_TOOL:CLAUDE -->
- For repo edits, use the ijproxy edit/write tools listed above. Generic edit/write fallbacks are forbidden unless ijproxy is unavailable.
<!-- /IF_TOOL:CLAUDE -->
<!-- IF_EDITION:ULTIMATE -->
- Never shell for file ops (`cat`, `sed`, `find`, `grep`) on repo paths, except the client fallback (`./community/tools/fd.cmd`, `./community/tools/rg.cmd`) when no MCP is available.
<!-- /IF_EDITION:ULTIMATE -->
<!-- IF_EDITION:COMMUNITY -->
- Never shell for file ops (`cat`, `sed`, `find`, `grep`) on repo paths, except the client fallback (`./tools/fd.cmd`, `./tools/rg.cmd`) when no MCP is available.
<!-- /IF_EDITION:COMMUNITY -->
- Shell OK for: git (prefer `git_status` if the tool is available), build/test.
- Outside repo: native shell permitted.

{{PARTIAL:knowledge-mcps}}

## Individual Preferences

**Local Preferences:** @./.ai/local.md

<!-- IF_TOOL:CLAUDE -->
{{PARTIAL:claude-only-individual-preferences}}
<!-- /IF_TOOL:CLAUDE -->
