# Content Module Dependency Validation

Entry point: `ContentModuleDependencyValidator` (`NodeIds.PLUGIN_CONTENT_MODULE_VALIDATION`).

## Overview

Validates dependencies of plugin content modules within each product using graph-only content module dependency edges computed by `ContentModuleDependencyPlanner`.

## Inputs

- Slot: `Slots.CONTENT_MODULE_PLAN` (content module dependency edges in the graph).
- Graph: product-to-plugin-to-content containment, content module dependency edges, loading modes.
- Config: product `allowMissingDependencies`.

## Rules

- For each product:
  - Collect content modules from bundled plugins (production and test content).
  - Compute available modules for the product: module sets, product content modules, and bundled plugin content modules.
  - For each content module:
    - Determine criticality (any incoming contains edge is EMBEDDED or REQUIRED).
    - Traverse transitive deps.
    - Missing check:
      - Critical modules: deps must be available in this product.
      - Non-critical modules: deps must exist somewhere as content (has content source).
      - Exclude `allowMissingDependencies`.
- Orphan dependencies (no content source anywhere) are always errors.

## Suppression and allowlists

- Only product `allowMissingDependencies` is honored.
- Suppressions in `suppressions.json` do not affect this validator.

## Output

- `MissingDependenciesError` per product (ruleName `PluginContentModuleValidation`).

## Auto-fix

- None.

## Non-goals

- Validation of module set or product content modules (handled by `ProductModuleSetValidator`).
- Plugin-level content dependency validation (handled by `PluginContentDependencyValidator`).
- Plugin-level structural loading validation (handled by `PluginContentStructureValidator`).

## Related

- [validation-rules.md](../validation-rules.md)
- [errors.md](../errors.md)
- [product-module-set.md](product-module-set.md)
- [plugin-content-dependency.md](plugin-content-dependency.md)
- [plugin-content-structure.md](plugin-content-structure.md)
