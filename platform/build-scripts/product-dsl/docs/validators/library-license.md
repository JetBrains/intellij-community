# Library License Validation

Entry point: `LibraryLicenseValidator` (`NodeIds.LIBRARY_LICENSE_VALIDATION`).

## Overview

Ensures every third-party library that reaches a distribution has a license entry in a `*LibraryLicenses.kt` file. The rule walks the graph, as the other two library rules do. The `third_party_libraries` build step reads the modules that a distribution packs. This rule reads the modules that the graph holds, and the production runtime closure of each one.

## Inputs

- Config: `libraryLicenses` on `ModuleSetGenerationConfig` (the license list).
- Plugin graph: every content module with a production content source, and the main module of every production plugin.
- JPS model: the module of each name through `ModuleOutputProvider.findModule`.
- JPS model: production runtime dependencies from `JpsJavaExtensionService.dependencies(module).recursively().includedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME)`.

## Rules

- Walk every content module that has a production content source. A module that only a test plugin declares as content ships nothing, so the rule skips it.
- Walk the main module of every plugin that is not a test plugin. The graph holds that module as a plugin, not as a content module.
- Skip a name that no JPS module matches.
- Read the production runtime libraries of each module, recursively. A module therefore also reports the libraries of its production runtime closure. The `third_party_libraries` build step makes the same call without the recursion, and it walks the packed modules instead.
- Key each library by `getLibraryFileName`.
- Skip a library that an implicit sub-library rule covers: a name that starts with `ktor-` or `io.ktor.` and is not `ktor-client`, and a name that starts with `skiko-awt-runtime-`.
- Skip the library `ant`.
- Report a library that has no entry in `libraryLicenses`.
- An empty license list turns the rule off.
- A non-empty license list with no module in scope is a failure.

## Suppression and allowlists

- No suppression and no allowlist are supported.
- The rule holds no module name list. A module that ships must be in the graph.

## Output

- Emits `MissingLibraryLicenseError` with the category `MISSING_LIBRARY_LICENSE`.

## Auto-fix

- No.

## Non-goals

- The Maven descriptor filter and the JetBrains group filter of the `third_party_libraries` build step. The rule applies neither filter, so it stays stricter than the build step.
- Validation of the license text or the license identifier in an entry.
- A module that only a plugin layout holds. The product DSL model holds no plugin layout, so the rule can never reach such a module. It is out of scope while the graph does not hold it, and while no module in the graph depends on it. `intellij.platform.eelHelper` and `intellij.builtInHelp` are of this shape. The `third_party_libraries` build step is then the only gate for the libraries of that module. Convert the module to a content module to bring it into this rule. `IMPLICIT_PLUGIN_PROJECT_LIBRARY_ALLOWLIST` in `JarPackager` tracks the same migration for a project library, see IJPL-252908.
- The Android plugin. `intellij.android.plugin` holds `studio-platform`, and `intellij.android.app-inspection.inspectors.network.model` holds `brotli-dec`. Android is an external plugin, so the owner of that plugin decides whether to convert either module.

## Related

- [validation-rules.md](../validation-rules.md)
- [errors.md](../errors.md)
- [library-module.md](library-module.md)
