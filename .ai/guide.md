{{PARTIAL:frontmatter}}

<!-- TEMPLATE:COMMENT -->
To regenerate, run `node .ai/scripts/render-guides.mjs`.
<!-- /TEMPLATE:COMMENT -->

**Critical:** These guidelines MUST be followed at all times.

**Reference Index:** @./.ai/guidelines.md

## Project Invariants

- The repository is a large monorepo with multiple IDE products and plugins.
- Module/plugin directories may contain their own AGENTS/CLAUDE instructions; follow them when present.
- `*.iml` files are the source of truth and auto-generate `BUILD.bazel` files.
- User-visible strings belong in `*.properties` for localization.

{{PARTIAL:module-specific}}

## Mandatory Rules

### After Code Changes

- {{COMPILATION_RULE}}
- After modifying `*.iml`, `BUILD.bazel`, or `.idea/` files: run `./build/jpsModelToBazel.cmd`.
- Run affected tests: `./tests.cmd` (or `node --test <file>` for `*.test.mjs`).
  Module-specific rules may override the runner. Skip if plugin has no tests. See [TESTING-internals](../../.ai/topics/TESTING-internals.md).

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
- Find files: `mcp__ijproxy__find`
- Search: `mcp__ijproxy__grep`
- List dir: `mcp__ijproxy__list_dir`
<!-- /IF_TOOL:CODEX -->
<!-- IF_TOOL:CLAUDE -->
- Read: `read`
- Edit: `edit`
- Write: `write`
- Find files: `glob`
- Search: `grep`
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
- Use `./tools/fd.cmd` instead of Glob and `./tools/rg.cmd` instead of Grep. These are the only allowed shell file ops on repo paths.

### IDE-backed semantic tools
Available via ijproxy or JetBrains MCP. Use these for semantic operations; avoid manual search/replace when a refactor exists.

- Inspections & symbol info: `get_file_problems`, `get_symbol_info`
- Refactors: `rename` (ijproxy) / `rename_refactoring` (JetBrains MCP); use for renames and avoid manual search/replace.
- Formatting: `reformat_file`
- Concurrency checks: `find_threading_requirements_usages`, `find_lock_requirements_usages`
- Project structure: `get_project_modules`, `get_project_dependencies`, `get_repositories`
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
- Never shell for file ops (`cat`, `sed`, `find`, `grep`) on repo paths, except the client fallback (`./tools/fd.cmd`, `./tools/rg.cmd`) when no MCP is available.
- Shell OK for: git, build/test.
- Outside repo: native shell permitted.

### Knowledge MCPs (auto-use)
- Glean: YouTrack issues, JetBrains knowledge base, `./docs` (use Glean search when needed).
- Context7: IntelliJ SDK docs, Kotlin docs.
- Plugin Model Analyzer: module structure, product composition.

<!-- IF_TOOL:CLAUDE -->
{{PARTIAL:claude-only}}
<!-- /IF_TOOL:CLAUDE -->
