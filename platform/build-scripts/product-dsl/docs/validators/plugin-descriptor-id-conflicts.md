# Plugin Descriptor ID Conflicts Validation

Entry point: `PluginDescriptorIdConflictValidator` (`NodeIds.PLUGIN_DESCRIPTOR_ID_CONFLICT_VALIDATION`).

## Overview

Detects descriptor IDs that are declared by both production and test plugins bundled in the same product. This prevents runtime
"Plugin declares id ... which conflicts with the same id from another plugin" errors during test runs.

## Inputs

- Graph: product bundling edges and plugin content edges for production and test plugins.

## Rules

- For each product:
  - Collect descriptor IDs declared by bundled production plugins:
    - Plugin ID (`<id>`, fallback to plugin module name if missing).
    - Content module IDs (module names).
  - Collect descriptor IDs declared by bundled test plugins with the same rules.
  - If a descriptor ID appears in both sets, report a conflict with all owners.

## Suppression and allowlists

- None.

## Output

- `PluginDescriptorIdConflictError` per product.

## Auto-fix

- None.

## Related

- [validation-rules.md](../validation-rules.md)
- [plugin-content-duplicates.md](plugin-content-duplicates.md)
- [errors.md](../errors.md)
