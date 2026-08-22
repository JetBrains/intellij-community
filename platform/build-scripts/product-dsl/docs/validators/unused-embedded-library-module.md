# Unused Embedded Library Module Validation

Entry point: `UnusedEmbeddedLibraryModuleValidator` (`NodeIds.UNUSED_EMBEDDED_LIBRARY_MODULE_VALIDATION`).

## Overview

Validates that `intellij.libraries.*` content modules use `embedded` loading only when embedded platform content needs them in at least one product.

## Inputs

- Slot: `Slots.CONTENT_MODULE_PLAN` (production and test content-module dependency edges in the graph).
- Graph: products, recursive module sets, plugin content, loading modes, content-module dependencies, and backing JPS target dependencies.

## Rules

- Collect library modules declared with `EMBEDDED` loading directly in any module set, including nested sets.
- For each product, start from embedded non-library product/module-set content.
- Traverse production content-module dependencies only while every dependency remains embedded product/module-set content in that product.
- Traverse production JPS dependencies from backing targets of embedded product/module-set content, stopping at descriptor-backed content that is not embedded in that product.
- A library is justified when this traversal reaches it in at least one product.
- Plugin content, test dependencies, and optional or required platform content do not justify core-classloader loading.
- Embedded library chains and cycles without a reachable non-library platform root remain violations.

## Suppression and allowlists

- `CORE_CLASSLOADER_ONLY_LIBRARIES` in `UnusedEmbeddedLibraryAnalysis.kt` excludes a library from the check entirely.
- The only accepted criterion: the library's classes are resolved from classloaders the layout cannot enumerate -
  generated proxies defined in a plugin classloader, or a factory/SPI lookup by class name from arbitrary code.
  A `<dependencies><module .../></dependencies>` edge cannot express "every plugin".
- Being convenient to have everywhere is not a reason: demote the library and add the edges.
- Current entries: `intellij.libraries.cglib` (`AdvancedEnhancer.getDefaultClassLoader()` defines each generated DOM
  proxy in the `PluginClassLoader` of one of the proxied interfaces).
- Otherwise unused core-classloader content is a hard validation failure.

## Output

- One `UnusedEmbeddedLibraryModuleError` containing sorted violations.
- Each violation includes declaring module sets, products where the module is available, direct platform consumers, and transitive production/test plugin consumers.
- Compact diagnostics are available through `{"filter":"validation","check":"unused_embedded_library_modules"}`.

## Non-goals

- Ordinary library content declared with `module(...)` — see [unused-shared-library-module.md](unused-shared-library-module.md).
- Embedded modules outside the `intellij.libraries.*` namespace.
- Choosing between shared optional content and private plugin content; consumers are reported so the owner can apply that policy.

## Related

- [validation-rules.md](../validation-rules.md)
- [embedded-content-module-dependency.md](embedded-content-module-dependency.md)
- [unused-shared-library-module.md](unused-shared-library-module.md)
