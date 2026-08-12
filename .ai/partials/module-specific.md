## Module-specific rules

For files under these roots, read the referenced rules before edits or reviews; they override conflicting general guidance.

<!-- IF_EDITION:ULTIMATE -->- **Product DSL** (`community/platform/build-scripts/product-dsl/`): follow its `AGENTS.md`.<!-- /IF_EDITION:ULTIMATE --><!-- IF_EDITION:COMMUNITY -->- **Product DSL** (`platform/build-scripts/product-dsl/`): follow its `AGENTS.md`.<!-- /IF_EDITION:COMMUNITY -->
- **IJ Proxy MCP server** (`community/build/mcp-servers/ij-proxy/`):
  - Tests: run `bun run build` and `bun test`.
  - Bazel: do not run Bazel build and tests here.
- **AI Assistant activation** (`plugins/llm/activation/`):
  - Activation: follow `plugins/llm/activation/.ai/guidelines.md` before edits or reviews.
- **Toolbox** (`toolbox/`):
  - Tests: never use `./tests.cmd`; see `toolbox/.ai/index.md` for Gradle/Bazel test commands.
  - Build: use `./bazel.cmd build //toolbox/...` instead of `./bazel-build-all.cmd`.
<!-- IF_EDITION:ULTIMATE -->- **PyCharm** (`python/` and `community/python/`): use `/community/python/.ai/index.md`.<!-- /IF_EDITION:ULTIMATE --><!-- IF_EDITION:COMMUNITY -->- **PyCharm** (`./python`): use `./python/.ai/index.md`.<!-- /IF_EDITION:COMMUNITY -->
