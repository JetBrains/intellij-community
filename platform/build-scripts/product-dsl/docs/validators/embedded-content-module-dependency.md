# Embedded Content Module Dependency Validation

Entry point: `EmbeddedContentModuleDependencyValidator` (`NodeIds.EMBEDDED_CONTENT_MODULE_DEPENDENCY_VALIDATION`).

## Overview

Validates that product/module-set `embedded` content modules do not depend on content modules that are supplied only by bundled plugin wrappers.

The underlying traversal can also report any non-embedded dependency boundary for diagnostics; the hard validator is intentionally scoped to bundled plugin content because existing product layouts still contain regular module-set boundaries that are not plugin classloader splits.

## Inputs

- Slot: `Slots.CONTENT_MODULE_PLAN` (content module dependency edges in the graph).
- Graph: product/module-set content, bundled plugin content, loading modes, and content-module dependency edges.

## Rules

- For each product:
  - Collect product and module-set content modules with `EMBEDDED` loading from non-plugin sources.
  - Walk their content-module dependency closure through embedded dependencies.
  - Report the first dependency boundary where the dependency is available in the product only through a bundled plugin and is not embedded in product/module-set content.
- Dependencies unavailable in the product are left to existing missing-dependency validation.

## Suppression and allowlists

- None. This is a classloader boundary rule, not a dependency availability rule.

## Output

- `EmbeddedContentModuleDependencyError` per product, including the source module, offending dependency, dependency path, and the dependency source/loading context.

## Non-goals

- Missing dependency reporting (handled by `ProductModuleSetValidator`).
- Plugin-local loading structure (handled by `PluginContentStructureValidator`).
- JPS-only dependencies not represented as content-module dependency edges.

## Related

- [validation-rules.md](../validation-rules.md)
- [product-module-set.md](product-module-set.md)
- [plugin-content-structure.md](plugin-content-structure.md)
