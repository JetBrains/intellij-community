# Plugin Content Duplicates Validation

Entry point: `PluginContentDuplicatesValidator` (`NodeIds.PLUGIN_CONTENT_DUPLICATE_VALIDATION`).

## Overview

Detects one runtime content module ID that two bundled plugins of one product declare.

The subject is the runtime ID. `PluginModuleId.toActualId` builds it from the module name and the namespace. The
runtime keeps one descriptor per ID. `resolveIdConflicts` drops a whole plugin to break the tie, so a shared ID makes
a bundled plugin disappear.

## Inputs

- Graph: product bundling edges and plugin content edges for production and test plugins.

## Rules

- For each product:
  - Collect the runtime content module IDs that the bundled production plugins declare, with the owner of each ID.
  - Collect the same for the bundled test plugins.
  - Report an ID that two production plugins declare.
  - Report an ID that a production plugin and a test plugin declare.
  - Keep an ID that only test plugins share, because a product loads one test plugin at a time.

## Suppression and allowlists

- None. No product in the repository holds such a pair today, so the rule has no grandfathered entry.

## Output

- `DuplicatePluginContentModulesError` per product. `PluginOwner.isTestPlugin` marks a test plugin owner.

## Auto-fix

- None.

## Non-goals

- A content module that two plugins declare in a `<content>` tag with no namespace. That shape is legal.
  `toActualId` gives each such copy an implicit namespace per plugin, so the runtime IDs differ and no pair forms.
  Read
  `docs/IntelliJ-Platform/4_man/Plugin-Model/Including-content-module-in-multiple-plugins.md`, which is IJPL-A-1893.
  Never report that shape here.
- The classloader cost of such a private copy. `ContentModuleCopyConflictValidator` holds that half of the question.
- A JPS module that two static plugin layouts pack. `collectModulesInMultiplePlugins` holds that shape.
- Module set duplication detection (handled by `ProductModuleSetValidator`).

## Related

- [validation-rules.md](../validation-rules.md)
- [errors.md](../errors.md)
- [product-module-set.md](product-module-set.md)
- [content-module-copy-conflict.md](content-module-copy-conflict.md)
- [module-in-multiple-plugins.md](module-in-multiple-plugins.md)
