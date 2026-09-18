# Plugin Manager Specifications

These specifications define stable plugin manager behavior in the community repository.

- [Unified Plugin Manager UI](./unified-plugin-manager-ui.spec.md) defines the page, sections, search, loading, and navigation behavior.
- [Plugin Operations](./plugin-operations.spec.md) defines operations, session changes, and Update All behavior.

## Local Format

Each specification has YAML frontmatter with `name`, `description`, and `targets`.
Its H1 matches `name`. It also has a status, an ISO date, verification links, and open questions.

Each specification uses sections that describe its own domain:

- The UI specification separates structure, membership, search, reporting, loading, interaction, presentation, integration, and source behavior.
- The operation specification separates lifecycle, Installing, the settings session, install and update, management, Update All, presentation, integration, and recovery.

The local verifier defines the required sections for each specification. It does not impose this format on another specification family.

Put `[@test]` links next to the behavior that they verify. Use `Untested:` when focused coverage does not exist.

Update the owning specification in the same changelist as a behavior change.

Report existing drift and suggest a separate repair. Do not include that repair in the active commit.

Run this reference gate from the community repository root:

```shell
./bazel.cmd test //platform/platform-impl:ide-impl-tests_test --test_filter=com.intellij.ide.plugins.PluginManagerSpecReferencesTest
```
