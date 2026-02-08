# Self-Contained Module Set Validation

Entry point: `SelfContainedModuleSetValidator` (`NodeIds.SELF_CONTAINED_VALIDATION`).

## Overview

Validates that module sets marked `selfContained = true` are internally complete: all transitive dependencies of modules in the set must be contained within the same module set hierarchy.

## Inputs

- Slot: `Slots.CONTENT_MODULE` (content module dependency edges in the graph).
- Graph: module set containment, module content sources, and content module dependencies.

## Rules

- For each module set marked `selfContained = true`:
  - Collect the full hierarchy of nested module sets.
  - For each module in the set:
    - Traverse transitive deps.
    - If a dependency is not contained by any module set in the hierarchy, report it as missing.

## Suppression and allowlists

- None.

## Output

- `SelfContainedValidationError` per self-contained module set.

## Auto-fix

- None.

## Non-goals

- Product-level module set validation (handled by `ProductModuleSetValidator`).

## Related

- [validation-rules.md](../validation-rules.md)
- [errors.md](../errors.md)
- [product-module-set.md](product-module-set.md)
