# Product Module Set Validation

Entry point: `ProductModuleSetValidator` (`NodeIds.PRODUCT_MODULE_SET_VALIDATION`).

## Overview

Validates product module set composition by checking duplicate modules and missing transitive dependencies for modules that make up a product.

## Inputs

- Graph: product-to-module-set containment, product content modules, plugin content modules, and content module dependencies.
- Config: product `allowMissingDependencies`.

## Rules

- For each product:
  - Collect modules from all module sets in the product hierarchy plus product content modules.
  - Detect duplicates (same module appears more than once across module sets or product content).
  - Compute available modules for the product: module set modules plus bundled plugin content modules.
  - For each collected module:
    - Determine criticality (any incoming contains edge is EMBEDDED or REQUIRED).
    - Traverse transitive deps.
    - Missing check:
      - Critical modules: deps must be available in this product.
      - Non-critical modules: deps must exist somewhere as content (has content source).
      - Exclude `allowMissingDependencies`.

## Suppression and allowlists

- Only product `allowMissingDependencies` is honored.

## Output

- `DuplicateModulesError` for duplicate module declarations.
- `MissingDependenciesError` for unresolved dependencies (ruleName `ProductModuleSetValidation`).

## Auto-fix

- None.

## Non-goals

- Validation of plugin content modules (handled by `ContentModuleDependencyValidator`).

## Related

- [validation-rules.md](../validation-rules.md)
- [errors.md](../errors.md)
- [content-module-dependency.md](content-module-dependency.md)
