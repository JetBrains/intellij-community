# Plugin Content Duplicates Validation

Entry point: `PluginContentDuplicatesValidator` (`NodeIds.PLUGIN_CONTENT_DUPLICATE_VALIDATION`).

## Overview

Detects content modules declared by both production and test plugins bundled in the same product. Such duplicates cause runtime errors when the IDE loads plugin content.

## Inputs

- Graph: product bundling edges and plugin content edges for production and test plugins.

## Rules

- For each product:
  - Collect content modules declared by bundled production plugins.
  - Collect content modules declared by bundled test plugins.
  - If a content module appears in both sets, report a duplicate with the owning plugins.

## Suppression and allowlists

- None.

## Output

- `DuplicatePluginContentModulesError` per product.

## Auto-fix

- None.

## Non-goals

- Module set duplication detection (handled by `ProductModuleSetValidator`).

## Related

- [validation-rules.md](../validation-rules.md)
- [errors.md](../errors.md)
- [product-module-set.md](product-module-set.md)
