# Library Module Validation

Entry point: `LibraryModuleValidator` (`NodeIds.LIBRARY_MODULE_VALIDATION`).

## Overview

Ensures modules do not depend directly on project libraries that are exported by library modules (`intellij.libraries.*`). Direct library deps are replaced with module deps or removed if already present.

## Inputs

- Graph: content modules (targets come from products/module sets).
- Config: `projectLibraryToModuleMap` built from JPS library modules.
- JPS model: module dependency lists from `.iml` files.
- Config: `libraryModuleFilter` for eligible library modules.
- Suppression: `suppressLibraries` per content module in `suppressions.json`.
- Mode: `updateSuppressions` (collects suppressions instead of applying fixes).

## Rules

- Use the library name -> library module map from `ModuleSetGenerationConfig.projectLibraryToModuleMap`.
- For each non-library content module (excluding allowlisted modules):
  - Inspect JPS library dependencies (project-level libraries only).
  - If a library maps to a library module and the filter allows it:
    - If the module does not already depend on the library module, replace the library dependency with a module dependency.
    - If the module already depends on the library module, remove the library dependency.
- Apply fixes only for non-suppressed libraries; collect suppression usage for suppressed entries.

## Suppression and allowlists

- `suppressLibraries` per content module skips replacements for those libraries.
- `updateSuppressions` collects suppression usage without modifying `.iml` files.

## Output

- Updates `.iml` files via the file updater.
- Publishes `Slots.LIBRARY_SUPPRESSIONS` (suppression usage).
- Does not emit validation errors.

## Auto-fix

- Yes. Rewrites `.iml` dependencies to use library modules.

## Non-goals

- Validation of runtime dependency resolution (handled by other validators).

## Related

- [validation-rules.md](../validation-rules.md)
- [errors.md](../errors.md)
- [test-library-scope.md](test-library-scope.md)
