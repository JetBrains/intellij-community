# Plugin-to-Plugin Dependency Validation

Entry point: `PluginPluginDependencyValidator` (`NodeIds.PLUGIN_PLUGIN_VALIDATION`).

## Overview

Validates that required plugin dependencies are bundled in the same products as the plugins that declare them.

## Inputs

- Graph: plugin dependency edges and product bundling edges.

## Rules

- For each plugin:
  - Collect products that bundle the plugin.
  - Collect required (non-optional) plugin dependencies.
  - For each dependency:
    - Resolve the target plugin node and track bundled products and presence of a main target.
    - If the dependency has no main target and is not bundled anywhere, mark as unresolved.
    - If the current plugin is bundled, ensure every bundling product also bundles the dependency plugin.

## Suppression and allowlists

- None.

## Output

- `PluginDependencyNotBundledError` with missing-by-product and unresolved dependency IDs.

## Auto-fix

- None.

## Non-goals

- Content module dependency validation (handled by `PluginContentDependencyValidator`).
- Duplicate dependency declaration detection (handled by `PluginDependencyDeclarationValidator`).

## Related

- [validation-rules.md](../validation-rules.md)
- [errors.md](../errors.md)
- [plugin-content-dependency.md](plugin-content-dependency.md)
- [plugin-dependency-declaration.md](plugin-dependency-declaration.md)
