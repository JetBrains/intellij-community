# Platform Library Copy Validation

Entry point: `PlatformLibraryCopyValidator` (`NodeIds.PLATFORM_LIBRARY_COPY_VALIDATION`).

## Overview

An embedded library module puts its library into the core classloader. A content module of a bundled plugin packs its module-level libraries into the jar of the plugin. `JarPackager` packs these libraries without a check against the core.

A plugin that declares `<depends>` on the owner of such a copy gets every content module of the owner as a parent classloader. The plugin classloader searches the parents before the core classloader. So the plugin can load the copy instead of the platform classes. IJAI-3760 is this case: `intellij.libraries.google.auth` in the Air plugin packed Guava, and a dependent plugin failed with `IncompatibleClassChangeError`.

This rule reports such a copy at build time.

## Inputs

- Plugin graph: the module sets of each product, the content of each product, and the content of each bundled production plugin.
- Config: `platformLibraryCopies` in `platform/buildScripts/suppressions.json`, for the allowed copies.
- JPS model: the module of each name through `ModuleOutputProvider.findModule`.
- JPS model: the `CLASSES` roots of each module-level repository library of a module.

## Rules

- For each product, collect the platform modules. A platform module is a library module, with the `intellij.libraries.` prefix, that a module set of the product or the product itself declares with the embedded loading rule.
- For each platform module, collect the Maven `groupId:artifactId` of each `CLASSES` root of its module-level libraries.
- For each content module of each bundled plugin of the product, collect the same coordinates.
- Report a coordinate of a content module that a platform module also provides. The version does not matter.
- Skip a content module that is itself a platform module of the product.
- Skip a content module in slash notation, because it has no JPS module of its own.
- Count a library dependency with the COMPILE or RUNTIME scope. Skip a library dependency with the PROVIDED or TEST scope, because the build does not pack it.
- Count a module-level library only. A project library and a module dependency do not count.
- Derive a coordinate from the path of a root. The JPS model holds a root with the repository path expanded. The root of the main artifact gives that path, from the `maven-id` of the library. A root outside that path does not count.
- The roots of a repository library are the resolved closure, so an `<exclude>` entry removes a coordinate.
- One error covers all products. A violation lists the products in which it occurs.

## Suppression and allowlists

`platformLibraryCopies` in `platform/buildScripts/suppressions.json` lists the allowed copies. The key is the content module name. The value holds the `coordinates` that the module may copy, and a free-text `reason`.

- The node applies the config itself, the way `ContentModuleCopyConflictValidator` does. So a unit test sees the config.
- The grain is the module and the coordinate. A new coordinate in a listed module still fails, and so does a new module.
- One entry covers the module in every plugin and every product.
- A `reason` that starts with `DEBT:` marks a copy that is expected to go away. Remove the entry when the copy goes away.
- `SuppressionConfigGenerator` copies the map without a change. A stale entry is not reported.

The rule came in with six `DEBT:` entries. Each entry names the platform module and the plugins in its `reason`.

## Output

- `PlatformLibraryCopyError` with the category `PLATFORM_LIBRARY_COPY`. The error ID is `platform-library-copy:bundled plugins`.
- Each violation names the plugin, the content module, the library, the coordinate with its version, the platform module, and the products.

## Auto-fix

- No. The error text gives the fix. Exclude the dependency, and add a module dependency on its wrapper.
- A content module without a namespace cannot name a wrapper with the `internal` visibility. Give such a module dependency the PROVIDED scope. Then the module has the classes at compile time, the generated descriptor leaves the dependency out, and the core classloader gives the classes at run time.

## Non-goals

- The main module of a plugin. The rule reads the content modules of a plugin only.
- A library that a module dependency of a content module adds. The rule reads the module-level libraries of the content module only.
- A platform module that is not embedded. Such a module has a classloader of its own, so a dependent plugin names it explicitly.
- A copy that a test plugin packs.
- The runtime order of the classloaders. `PluginClassLoader` stays unchanged.

## Related

- [validation-rules.md](../validation-rules.md)
- [errors.md](../errors.md)
- [library-license.md](library-license.md)
