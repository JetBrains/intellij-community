# Test Plugin Plugin Dependency Validation

Entry point: `TestPluginPluginDependencyValidator` (`NodeIds.TEST_PLUGIN_PLUGIN_DEPENDENCY_VALIDATION`).

## Overview

Validates that DSL-defined test plugins declare the plugin dependencies required by their content modules.
These dependencies are derived from JPS module deps (including TEST scope) and must be present in the
generated test plugin `plugin.xml` to keep the test classpath consistent.

## Inputs

- Slot: `Slots.CONTENT_MODULE` (content module deps with test scope, from `ContentModuleDependencyGenerator`).
- Slot: `Slots.TEST_PLUGINS` (ensures generated test plugin XML is available via the deferred file updater).
- Graph: owning plugin resolution and product bundling edges.
- Model: `dslTestPluginsByProduct`, `dslTestPluginDependencyChains`, `fileUpdater`.
- Spec: test plugin `allowedMissingPluginIds`, module-level `allowedMissingPluginIds`, and `additionalBundledPluginTargetNames`.

## Rules

- For each DSL test plugin spec in each product:
  - Build the content module list from the spec (`buildContentBlocksAndChainMapping`).
  - Read the generated test plugin XML (expected content from the deferred updater if present, otherwise disk).
  - Parse declared plugin dependencies from `<dependencies><plugin/>` and legacy `<depends>` entries.
  - For each content module in the test plugin:
    - Use `DependencyFileResult.testDependencies` to get module dependencies (includes TEST scope).
    - Ignore dependencies that are:
      - Already part of the test plugin content.
      - Library wrapper modules (`intellij.libraries.*`).
      - Not owned by any production plugin.
    - For each dependency module owned by production plugin(s) that are resolvable in test scope
      (bundled in the product or listed in `additionalBundledPluginTargetNames`):
      - Require a `<plugin id="..."/>` entry for each owning plugin ID,
        unless that plugin ID is allowed-missing for the module or the test plugin.
  - Use `dslTestPluginDependencyChains` to apply module-level `allowedMissingPluginIds` to auto-added modules
    via their root declared module.

## Suppression and allowlists

- `TestPluginSpec.allowedMissingPluginIds` (global for the test plugin).
- Module-level `allowedMissingPluginIds` from module sets and additional modules.
- Auto-added modules inherit allowlists from their root declared module via dependency chains.

## Output

- `MissingTestPluginPluginDependencyError`.

## Non-goals

- Unresolvable plugin-owned content for DSL test plugins (handled by `DslTestPluginDependencyError`).
- Content module XML plugin dependency validation.
- Production plugin-to-plugin bundling validation.
