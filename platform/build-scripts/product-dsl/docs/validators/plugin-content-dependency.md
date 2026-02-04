# Plugin Content Dependency Validation

Entry points: `PluginContentDependencyValidator` (`NodeIds.PLUGIN_VALIDATION`) and
`ContentModulePluginDependencyValidator` (`NodeIds.CONTENT_MODULE_PLUGIN_DEPENDENCY_VALIDATION`).

## Overview

Validates runtime dependency correctness for plugin content modules and plugin-level content dependencies.

## Inputs

- Slot: `Slots.CONTENT_MODULE` (results from `ContentModuleDependencyGenerator`).
- Graph: plugin content and dependency edges (`EDGE_CONTAINS_CONTENT`, `EDGE_CONTAINS_CONTENT_TEST`, `EDGE_CONTENT_MODULE_DEPENDS_ON`, `EDGE_CONTENT_MODULE_DEPENDS_ON_TEST`, plugin `dependsOnContentModule`).
- Model: `buildPluginValidationModel()` (resolution sources + graph queries for bundling and plugin types).
- Config: `pluginAllowedMissingDependencies`, product `allowMissingDependencies`, `contentModuleAllowedMissingPluginDeps`, and `suppressionConfig`.

## Rules

- Build a unified validation model with resolution sources from module sets, plugin content, and product content.
- For each plugin:
  - Determine plugin type (production, discovered test, DSL test) and select the validation predicate.
  - Collect content modules and their loading modes from graph edges.
  - Collect content module dependencies from graph edges:
    - Production plugins use `EDGE_CONTENT_MODULE_DEPENDS_ON`.
    - Test plugins include `EDGE_CONTENT_MODULE_DEPENDS_ON` plus `EDGE_CONTENT_MODULE_DEPENDS_ON_TEST`.
  - Collect plugin-level content module dependencies (`dependsOnContentModule`) that are not part of the plugin’s own content.
  - Partition dependencies into required vs on-demand based on loading modes and explicit plugin-level deps.
  - Availability check:
    - Bundled plugins: required deps must resolve in every bundling product.
    - Non-bundled plugins: required deps must exist globally.
  - On-demand check: on-demand deps must exist globally.
  - Filtered dependency check: implicit dependencies (JPS deps missing from XML) must resolve unless suppressed or allowlisted.
- Content module plugin dependency check (ContentModulePluginDependencyValidator):
  - For each content module descriptor, ensure plugin IDs for JPS deps on plugin main modules are declared in XML `<dependencies><plugin id=.../>`, excluding containing plugins.

## Suppression and allowlists

- `suppressionConfig` suppresses module deps for content modules; suppressed implicit deps are excluded from validation.
- `pluginAllowedMissingDependencies` and product `allowMissingDependencies` exclude specific module deps.
- `contentModuleAllowedMissingPluginDeps` excludes specific missing plugin IDs for the content module plugin-dep check.
- This validator must not report errors for dependencies intentionally suppressed from XML.

## Output

- `PluginDependencyError` for module dependency resolution and filtered deps.
- `MissingContentModulePluginDependencyError` for missing plugin IDs in content module XML.

## Non-goals

- Plugin-to-plugin dependency validation (handled by `PluginPluginDependencyValidator`).
- Product/module-set dependency validation (handled by `ProductModuleSetValidator` and `ContentModuleDependencyValidator`).
- Validation of raw JPS deps that are suppressed from the graph.
- Loading-mode structural validation (handled by `PluginContentStructureValidator`).

## Related

- [validation-rules.md](../validation-rules.md)
- [errors.md](../errors.md)
- [dependency_generation.md](../dependency_generation.md)
- [content-module-dependency.md](content-module-dependency.md)
- [plugin-content-structure.md](plugin-content-structure.md)
- [plugin-plugin-dependency.md](plugin-plugin-dependency.md)
