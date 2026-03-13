# Test Plugin Plugin Dependency Validation

Entry point: `TestPluginPluginDependencyValidator` (`NodeIds.TEST_PLUGIN_PLUGIN_DEPENDENCY_VALIDATION`).

## Overview

Validates that DSL-defined test plugins declare the plugin dependencies required by their content modules.
These dependencies are derived from JPS module deps (including TEST scope) and must be present in the
generated test plugin `plugin.xml` to keep the test classpath consistent.

## Inputs

- Slot: `Slots.TEST_PLUGIN_DEPENDENCY_PLAN` (dependency plan from `TestPluginDependencyPlanner`).
- Slot: `Slots.TEST_PLUGINS` (ensures generated test plugin XML is available via the deferred file updater).
- Graph: owning plugin resolution and product bundling edges.
- Model: `dslTestPluginsByProduct`, `dslTestPluginDependencyChains`, `fileUpdater`.
- Spec: test plugin `allowedMissingPluginIds`, module-level `allowedMissingPluginIds`, and `additionalBundledPluginTargetNames`.

## Rules

- For each DSL test plugin spec in each product:
  - Read the generated test plugin XML (expected content from the deferred updater if present, otherwise disk).
  - Parse declared plugin dependencies from `<dependencies><plugin/>` and legacy `<depends>` entries.
  - Use `TestPluginDependencyPlan.requiredByPlugin` for plugin dependencies required by content modules.
  - Apply module-level and global `allowedMissingPluginIds`, plus `dslTestPluginDependencyChains`,
    to filter required dependencies per module.
  - Report missing plugin dependencies that are required but not declared.
  - Report unresolvable dependencies from the dependency plan unless suppressed.

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
