## Module-specific rules

Read the referenced rules before you edit or review a file under these roots. They override the general guidance here.

- **Product DSL** (`{{COMMUNITY_DIR}}platform/build-scripts/product-dsl/`): follow its `AGENTS.md`.
- **IJ Proxy MCP server** (`{{COMMUNITY_DIR}}build/mcp-servers/ij-proxy/`):
  - Tests: run `bun run build` and `bun test`.
  - Bazel: do not run a Bazel build or test here.
<!-- IF_EDITION:ULTIMATE -->- **PyCharm** (`python/` and `community/python/`): use `/community/python/.ai/index.md`.<!-- /IF_EDITION:ULTIMATE --><!-- IF_EDITION:COMMUNITY -->- **PyCharm** (`./python`): use `./python/.ai/index.md`.<!-- /IF_EDITION:COMMUNITY -->
