# Plugin Descriptor ID Conflicts Validation

Entry point: `PluginDescriptorIdConflictValidator` (`NodeIds.PLUGIN_DESCRIPTOR_ID_CONFLICT_VALIDATION`).

## Overview

Detects descriptor IDs that are declared by both production and test plugins bundled in the same product. This prevents runtime
"Plugin declares id ... which conflicts with the same id from another plugin" errors during test runs.

## Inputs

- Graph: product bundling edges and plugin content edges for production and test plugins.
- DSL test plugin specs (`GenerationModel.dslTestPluginsByProduct`), for `additionalBundledPluginTargetNames`.

## Rules

- For each product:
  - Collect descriptor IDs declared by bundled production plugins:
    - Plugin ID (`<id>`, fallback to plugin module name if missing).
    - Content module IDs (module names), only for modules in the `jetbrains` namespace - a namespace-less
      module is registered in the plugin's implicit namespace at runtime and cannot collide.
  - For each bundled test plugin, compare its own descriptor IDs against those owners **plus** the ones
    declared by the plugins in its `additionalBundledPluginTargetNames`. Such a plugin is not bundled by the
    product, but the test plugin's dev-build runner adds it to the run (mirroring `-Dadditional.modules=` in
    `intellij.yaml`), so at runtime it owns descriptor IDs just like a bundled one.
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
