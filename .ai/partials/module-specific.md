## Module-specific rules

Read the referenced rules before you edit or review a file under these roots. They override the general guidance here.

<!-- IF_EDITION:ULTIMATE -->- **Product DSL** (`community/platform/build-scripts/product-dsl/`): follow its `AGENTS.md`.<!-- /IF_EDITION:ULTIMATE --><!-- IF_EDITION:COMMUNITY -->- **Product DSL** (`platform/build-scripts/product-dsl/`): follow its `AGENTS.md`.<!-- /IF_EDITION:COMMUNITY -->
- **IJ Proxy MCP server** (`community/build/mcp-servers/ij-proxy/`):
  - Tests: run `bun run build` and `bun test`.
  - Bazel: do not run a Bazel build or test here.
- **AI Assistant activation** (`plugins/llm/activation/`): follow `plugins/llm/activation/.ai/guidelines.md`.
- **Toolbox** (`toolbox/`):
  - Tests: never use `./tests.cmd`. See `toolbox/.ai/index.md` for the Gradle and Bazel test commands.
  - Build: use `./bazel.cmd build //toolbox/...` instead of `./bazel-build-all.cmd`.
<!-- IF_EDITION:ULTIMATE -->- **PyCharm** (`python/` and `community/python/`): use `/community/python/.ai/index.md`.<!-- /IF_EDITION:ULTIMATE --><!-- IF_EDITION:COMMUNITY -->- **PyCharm** (`./python`): use `./python/.ai/index.md`.<!-- /IF_EDITION:COMMUNITY -->
