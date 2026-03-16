# Plugin Content Structural Validation

Entry point: `PluginContentStructureValidator` (`NodeIds.PLUGIN_CONTENT_STRUCTURE_VALIDATION`).

## Overview

Validates loading-mode constraints between content modules within the same plugin.

## Inputs

- Slot: `Slots.CONTENT_MODULE` (content module dependency edges in the graph).
- Graph: plugin content containment edges and content-module dependency edges.
- Plugin content cache and file updater (for structural auto-fix on non-DSL plugins).

## Rules

- For each plugin with a resolved plugin ID:
  - Collect content modules and their loading modes.
  - For test plugins, include both production and test content modules.
  - Collect module dependencies from graph edges:
    - Production plugins use `EDGE_CONTENT_MODULE_DEPENDS_ON`.
    - Test plugins include `EDGE_CONTENT_MODULE_DEPENDS_ON` plus `EDGE_CONTENT_MODULE_DEPENDS_ON_TEST`.
  - Consider only dependencies where the dependency module is also a content module of the same plugin.
  - Apply structural rules:
    - `EMBEDDED` cannot depend on non-`EMBEDDED` siblings.
    - `REQUIRED` cannot depend on `OPTIONAL` or `ON_DEMAND` siblings.

## Suppression and allowlists

- None.

## Output

- `PluginDependencyError` with `structuralViolations` populated (ruleName `pluginContentStructureValidation`).

## Auto-fix

- For non-DSL plugins, the validator rewrites `plugin.xml` to set `loading="required"` on the violating dependency modules.
- DSL-defined plugins are not modified; fix in Kotlin DSL instead.

## Non-goals

- Dependency availability validation (handled by `PluginContentDependencyValidator`).
- Plugin-to-plugin dependency validation (handled by `PluginPluginDependencyValidator`).

## Related

- [validation-rules.md](../validation-rules.md)
- [errors.md](../errors.md)
- [plugin-content-dependency.md](plugin-content-dependency.md)
