# Unused Shared Library Module Validation

Entry point: `UnusedSharedLibraryModuleValidator` (`NodeIds.UNUSED_SHARED_LIBRARY_MODULE_VALIDATION`).

## Overview

Companion of [unused-embedded-library-module.md](unused-embedded-library-module.md) for the other loading mode.
That rule asks whether a library deserves the core classloader; this one asks whether it deserves to be in the
product at all.

Validates that `intellij.libraries.*` content modules declared as ordinary (non-`embedded`) module-set content
have at least one consumer.

## Inputs

- Slot: `Slots.CONTENT_MODULE_PLAN` (production and test content-module dependency edges in the graph).
- Graph: module sets and loading modes, content-module dependencies, plugin-level module dependencies,
  product content, and bundled plugins.

## Rules

- Collect library modules declared with any loading mode other than `EMBEDDED` in any module set, including
  nested sets.
- A candidate is justified by a production or test dependency from a non-library content module, or by a
  plugin-level `<dependencies><module>` declaration.
- A dependency from another `intellij.libraries.*` wrapper does not justify either module: a chain of unused
  wrappers is still unused.
- A candidate with no consumer at all is a violation.

## Over-shipping diagnostic

A library that has consumers but is shipped in products where none of its consumers is available is reported as
`overShipped`, not as a violation. Shipping a shared library more widely than its consumers is a layout smell
rather than a build error — usually it means the library belongs in a narrower module set, or in the private
content of its owning plugin. Read it through
`{"filter":"validation","check":"unused_shared_library_modules"}`.

## Suppression and allowlists

- None. A library nothing depends on is a hard validation failure.

## Output

- One `UnusedSharedLibraryModuleError` containing sorted violations.
- Each violation includes declaring module sets and the products shipping the module.
- Compact diagnostics are available through `{"filter":"validation","check":"unused_shared_library_modules"}`,
  including the `overShipped` list with per-product detail and the consumers found elsewhere.

## Non-goals

- Embedded library content — see [unused-embedded-library-module.md](unused-embedded-library-module.md).
- Non-library content modules.
- Choosing between shared content and private plugin content; consumers are reported so the owner can apply
  that policy.

## Limitations

The rule builds its candidate set from module-set membership only. `analyzeUnusedSharedLibraryModules`
(`src/traversal/UnusedSharedLibraryAnalysis.kt`) collects the candidates in the `moduleSets { }` walk. It then
reads the consumers from content-module and plugin dependency edges only.

Therefore two kinds of consumers stay invisible. The first is a plain JPS `orderEntry` from a module outside the
graph. For example, `intellij.platform.buildScripts` uses `intellij.libraries.netty.tcnative.boringssl` this way.
The second is the legacy `PluginLayout.withModule(...)` path. For example, the Gradle plugin ships
`intellij.libraries.groovy.ant` through `CommunityRepositoryModules.kt`.

A wrapper that no module set lists is a blind spot. The rule does not validate it as used, and it does not report
it as unused.

## Related

- [validation-rules.md](../validation-rules.md)
- [unused-embedded-library-module.md](unused-embedded-library-module.md)
