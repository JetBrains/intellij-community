## Module-specific rules

Special handling applies to the directories below. If a file you touch lives under one of these roots, you must activate that module's rules first (read the referenced doc before edits or reviews). These rules override general guidelines if they conflict.

<!-- IF_TOOL:CODEX -->
- **Product DSL** (`community/platform/build-scripts/product-dsl/`): read `./.claude/rules/product-dsl.md` before changing anything in this tree.
<!-- /IF_TOOL:CODEX -->

- **Task MCP server** (`community/build/mcp-servers/task/`):
  - Tests: `community/build/mcp-servers/task/task-mcp.test.mjs`.
  - Bazel: do not run Bazel build and tests here.
- **IJ Proxy MCP server** (`community/build/mcp-servers/ij-proxy/`):
  - Tests: run `bun run build` and `bun test`.
  - Bazel: do not run Bazel build and tests here.
- **AI Assistant activation** (`plugins/llm/activation/`):
  - Activation: follow `plugins/llm/activation/.ai/guidelines.md` before edits or reviews.
