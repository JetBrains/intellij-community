## Module-specific rules

Read the referenced rules before you edit or review a file under these roots. They override the general guidance here.

- **Product DSL** (`{{COMMUNITY_DIR}}platform/build-scripts/product-dsl/`): follow its `AGENTS.md`.
- **IJ Proxy MCP server** (`{{COMMUNITY_DIR}}build/mcp-servers/ij-proxy/`):
  - Tests: run `bun run build` and `bun test`.
  - Bazel: do not run a Bazel build or test here.
- **Eel / IJent** (`{{COMMUNITY_DIR}}platform/eel*/`, `{{COMMUNITY_DIR}}platform/ijent/`<!-- IF_EDITION:ULTIMATE -->, `platform/ijent/`<!-- /IF_EDITION:ULTIMATE -->): read `{{COMMUNITY_DIR}}platform/eel/AGENTS.md` first.<!-- IF_EDITION:ULTIMATE --> The Eel docs are public; the IJent internals start from `platform/ijent/AGENTS.md`.<!-- /IF_EDITION:ULTIMATE -->
<!-- IF_EDITION:ULTIMATE -->- **PyCharm** (`python/`, `community/python/`): use the `pycharm-dev-workflow` skill for a PY-NNNNN issue or any Python support work.<!-- /IF_EDITION:ULTIMATE --><!-- IF_EDITION:COMMUNITY -->- **PyCharm** (`./python`): start a PY-NNNNN issue at `./python/.ai/index.md`.<!-- /IF_EDITION:COMMUNITY -->
