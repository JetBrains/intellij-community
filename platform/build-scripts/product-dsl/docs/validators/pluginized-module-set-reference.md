# Pluginized Module-Set Reference Validation

Entry point: `PluginizedModuleSetReferenceValidator` (`NodeIds.PLUGINIZED_MODULE_SET_REFERENCE_VALIDATION`).

## Overview

Validates that module sets materialized as standalone bundled plugin wrappers are not referenced through regular `moduleSet(...)` composition APIs.

## Inputs

- Discovery data: discovered module sets, discovered products, and test product specs.
- Product specs: `ProductModulesContentSpec.moduleSets` entries, including references with loading overrides.
- Module-set nesting graph from DSL definitions.

## Rules

- For each discovered product and test product spec:
  - Inspect all `moduleSet(...)` references.
  - If the referenced set has `pluginSpec != null`, emit `PluginizedModuleSetReferenceError`.
- For each non-pluginized module set:
  - Traverse its transitive nested sets.
  - If any nested set has `pluginSpec != null`, emit `PluginizedModuleSetReferenceError` for the owning regular module set.
- Do not report pluginized module sets as referencing themselves.
- Structural rules for pluginized roots themselves remain the responsibility of `ModuleSetPluginizationValidator`.

## Suppression and allowlists

- None. These are hard authoring errors.

## Output

- `PluginizedModuleSetReferenceError` with ruleName `PluginizedModuleSetReferenceValidation`.

## Auto-fix

- None.

## Non-goals

- Embedded-module or nested-pluginized-set constraints of pluginized roots.
- Product dependency availability or duplicate content checks.

## Related

- [validation-rules.md](../validation-rules.md)
- [errors.md](../errors.md)
- [product-module-set.md](product-module-set.md)
