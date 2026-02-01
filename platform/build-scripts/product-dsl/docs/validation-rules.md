# Validation Rules

Validation ensures module and plugin dependencies are resolvable at runtime and that descriptors remain consistent with the graph. The authoritative, validator-level specs live in [docs/validators/](validators/README.md).

## Rule Index

| # | Validator | Scope | Spec |
| --- | --- | --- | --- |
| 1 | Self-contained module set | Module set | [self-contained-module-set.md](validators/self-contained-module-set.md) |
| 2 | Product module set | Product | [product-module-set.md](validators/product-module-set.md) |
| 3 | Content module dependencies | Bundled plugins | [content-module-dependency.md](validators/content-module-dependency.md) |
| 4 | Plugin content dependencies | Plugin | [plugin-content-dependency.md](validators/plugin-content-dependency.md) |
| 5 | Plugin-to-plugin dependencies | Plugin | [plugin-plugin-dependency.md](validators/plugin-plugin-dependency.md) |
| 6 | Plugin dependency declaration duplicates | Plugin | [plugin-dependency-declaration.md](validators/plugin-dependency-declaration.md) |
| 7 | Duplicate plugin content modules | Product | [plugin-content-duplicates.md](validators/plugin-content-duplicates.md) |
| 8 | Test plugin descriptor ID conflicts | Product | [plugin-descriptor-id-conflicts.md](validators/plugin-descriptor-id-conflicts.md) |
| 9 | Library module replacement | Module | [library-module.md](validators/library-module.md) |
| 10 | Test library scope | Module | [test-library-scope.md](validators/test-library-scope.md) |
| 11 | Suppression config keys | Config | [suppression-config.md](validators/suppression-config.md) |
| 12 | Plugin content structural validation | Plugin | [plugin-content-structure.md](validators/plugin-content-structure.md) |

## When Validation Runs

- IDE: run configuration "Generate Product Layouts"
- CLI: `UltimateModuleSets.main()` or `CommunityModuleSets.main()`
- Bazel: `bazel run //platform/buildScripts:plugin-model-tool`

## Terminology

- plugin: a plugin entity in the graph (plugin node) with a descriptor; plugin.xml is the file format.
- content module: a module declared as content in a plugin, module set, or product.
- target/JPS module: build-time module from `.iml` or Bazel target; not validated directly.
- plugin dependency: dependency on another plugin (plugin-to-plugin).
- content module dependency: dependency on a module descriptor (module-to-module).

See [docs/validators/README.md](validators/README.md) for the full glossary.

## Global Semantics

### Graph-first validation

Validation uses the plugin graph after generation, filtering, and suppression. If a dependency is not represented as a graph edge, it is not validated.

### Loading modes

| Loading | Meaning | Validation scope |
| --- | --- | --- |
| embedded | Core module, main classloader | Per-product (must resolve in bundling product) |
| required | Required at startup | Per-product (must resolve in bundling product) |
| optional | Loaded if deps available | Global existence |
| on_demand | Loaded when requested | Global existence |
| (default) | Defaults to optional | Global existence |

Critical modules are those with embedded/required loading on any incoming content edge.

### Resolved vs orphan modules

- Resolved: module has at least one content source edge (plugin, module set, or product).
- Orphan: module is referenced by dependency edges but has no content source.

Orphan dependencies are always errors. For non-critical modules, availability is checked globally; for critical modules, availability must be within the bundling product.

### Suppression contract

Suppressions are explicit contracts: dependencies intentionally omitted from XML (via `suppressions.json` or allowlists) must not produce validation errors. Validators operate on the graph after filtering and suppression, not on raw JPS dependencies.

## Configuration knobs

- `allowMissingDependencies` (product): allow missing module deps in a product.
- `pluginAllowedMissingDependencies` (config): allow missing module deps for a plugin.
- `contentModuleAllowedMissingPluginDeps` (config): allow missing plugin IDs for a content module.
- `suppressions.json`: suppress module deps, plugin deps, library replacements, or test-library scope fixes.
- `dependencyFilter`: generation-time filter that affects which JPS deps become XML deps; implicit deps that survive suppression are still validated by plugin content validation.

## See also

- [docs/validators/README.md](validators/README.md)
- [errors.md](errors.md)
- [dependency_generation.md](dependency_generation.md)
- [module-sets.md](module-sets.md)
