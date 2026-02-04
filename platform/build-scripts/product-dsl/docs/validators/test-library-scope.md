# Test Library Scope Validation

Entry point: `TestLibraryScopeValidator` (`NodeIds.TEST_LIBRARY_SCOPE_VALIDATION`).

## Overview

Ensures testing libraries are not used in production scopes for production content modules. Test plugin content modules and allowlisted modules are excluded.

## Inputs

- Graph: content modules and their content sources (to detect test plugins).
- JPS model: module dependency lists from `.iml` files.
- Config: `testingLibraries` and `testLibraryAllowedInModule`.
- Suppression: `suppressTestLibraryScope` per content module in `suppressions.json`.
- Mode: `updateSuppressions` (collects suppressions instead of applying fixes).

## Rules

- For each content module (excluding library modules, test framework modules, and content from test plugins):
  - Inspect JPS library dependencies.
  - If the library is in `testingLibraries` and the dependency scope is not TEST:
    - Skip if allowlisted for the module.
    - Otherwise record a violation.
- Apply fixes only for non-suppressed libraries; collect suppression usage for suppressed entries.

## Suppression and allowlists

- `testLibraryAllowedInModule` allows specific testing libraries in production scope for a module.
- `suppressTestLibraryScope` suppresses fixes and records suppression usage.
- `updateSuppressions` collects suppression usage without modifying `.iml` files.

## Output

- Updates `.iml` files via the file updater.
- Publishes `Slots.TEST_LIBRARY_SCOPE_SUPPRESSIONS` (suppression usage).
- Does not emit validation errors.

## Auto-fix

- Yes. Rewrites `.iml` dependencies to use TEST scope.

## Non-goals

- Validation of module dependency resolution (handled by other validators).

## Related

- [validation-rules.md](../validation-rules.md)
- [errors.md](../errors.md)
- [library-module.md](library-module.md)
