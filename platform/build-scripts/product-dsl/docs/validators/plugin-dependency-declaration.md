# Plugin Dependency Declaration Validation

Entry point: `PluginDependencyDeclarationValidator` (`NodeIds.PLUGIN_DEPENDENCY_DECLARATION_VALIDATION`).

## Overview

Detects duplicated plugin dependency declarations between legacy `<depends>` and modern `<dependencies><plugin/>` formats.

## Inputs

- Graph: plugin dependency edges annotated with legacy/modern declaration flags.

## Rules

- For each plugin:
  - Inspect dependency edges that have both legacy and modern declaration flags.
  - Collect duplicate plugin IDs and report a single error per plugin if any exist.

## Suppression and allowlists

- None.

## Output

- `DuplicatePluginDependencyDeclarationError` for plugins that declare the same dependency in both formats.

## Auto-fix

- None.

## Non-goals

- Plugin-to-plugin dependency availability checks (handled by `PluginPluginDependencyValidator`).
- Content module dependency validation (handled by `PluginContentDependencyValidator`).

## Related

- [validation-rules.md](../validation-rules.md)
- [errors.md](../errors.md)
- [plugin-plugin-dependency.md](plugin-plugin-dependency.md)
