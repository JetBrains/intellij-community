# Content Module Dependency Declaration Validation

Entry point: `ContentModuleDependencyDeclarationValidator` (`NodeIds.CONTENT_MODULE_DEPENDENCY_DECLARATION_VALIDATION`).

## Overview

Checks the form of each `<dependencies>` entry of a content module descriptor. The subject is the text of the
descriptor, and not the reachability of a module in a product. So the rule runs once per descriptor, and not once per
product.

## Inputs

- Descriptor cache: `existingPluginDependencies` and `existingModuleDependencies` of each content module descriptor.
  Both keep the order and the duplicates of the file, which the duplicate rule needs.
- Plugin content cache: the plugin aliases of each plugin descriptor.
- Graph: the `<content>` entries of every plugin, the plugin id of every plugin main module, the alias plugin nodes,
  and the namespace of each `<content>` entry.
- `suppressions.json` through `SuppressionConfig.getAllowedMissingPlugins`.
- Slot `CONTENT_MODULE_PLAN`, for the order only. The plan holds the plugin dependencies as a `Set`, so it loses a
  duplicate.

## Rules

For each content module that at least one plugin declares as content:

- `<plugin id="com.intellij.modules.java">` names the Java plugin with the old alias. Use `com.intellij.java`.
- `<plugin id="com.intellij.modules.platform">` is redundant next to another module dependency. Every plugin gets the
  platform. The element stays legal when it is the only module dependency, because the plugin verifier still needs it.
  See MP-7413.
- `<plugin id="com.intellij.modules.kotlin.k1">` stays silent. The IDE never loads K1.
- A plugin id that no plugin main module, no alias plugin node, and no descriptor alias defines is unresolved. An id
  under `com.intellij.modules.os.` is an OS requirement, so it always resolves.
- The same plugin id must not appear two times in one descriptor.
- A `<module name="...">` element must not name the main module of a plugin. Use a `<plugin id="...">` element.
- An `internal` content module must not be used from another namespace. The namespace comes from the `<content>` entry
  of the owning plugin, so a module with two owners gets one report per distinct namespace.

A plugin id that equals the id of an owning plugin is skipped. The check for it is present, and commented out, until
the repository fixes the violations.

## Suppression and allowlists

- `SuppressionConfig.getAllowedMissingPlugins` allows an unresolved plugin id per content module. It reads the explicit
  `validationExceptions.<module>.allowMissingPlugins` entry, and the `contentModules.<module>.suppressPlugins` entry.
  A suppressed plugin dependency is a dependency that the generator must not add, so its id must not fail here either.
- The system property `intellij.platform.plugin.modules.check.visibility=disabled` turns off the namespace rule.

## Output

- `ContentModuleDependencyDeclarationError`, one per content module, with one problem per violation.

## Auto-fix

- None. Each problem carries the text of the fix.

## Non-goals

- A module dependency that names no known module, and a module that no plugin declares as content. Both belong to
  `ContentModuleDependencyValidator`.
- A dependency of a required or embedded module on an optional sibling. It belongs to `PluginDependencyResolution`.
- A `private` module of another plugin. It belongs to the resolution query, `ResolutionQuery.isVisibleFrom`.
- The `depends` element. `ContentParseResult` does not carry it, so `PluginModelValidator` keeps those two checks.

## Related

- [validation-rules.md](../validation-rules.md)
- [errors.md](../errors.md)
- [content-module-dependency.md](content-module-dependency.md)
