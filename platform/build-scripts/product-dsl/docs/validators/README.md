# Validators

This directory is the normative spec for product-dsl validation behavior. Each file maps to a single pipeline validator (the entry point) and defines what it validates, which inputs it uses, how suppression is applied, and which errors or auto-fixes it produces. Examples live in tests; these specs are intentionally example-free.

## Terminology

- plugin: a plugin entity in the graph (plugin node) with a descriptor; plugin.xml is the file format.
- content module: a module declared as content in a plugin, module set, or product.
- target/JPS module: build-time module from `.iml` or Bazel target; not validated directly.
- plugin dependency: dependency on another plugin (plugin-to-plugin).
- content module dependency: dependency on a module descriptor (module-to-module).
- suppressed dependency: a JPS-derived dependency intentionally omitted from XML by `suppressions.json` or allowlists; suppressed deps are excluded from validation.

## Graph-first contract

- Validation uses the plugin graph after generation, filtering, and suppression. If a dependency is not represented as a graph edge, it is not validated.
- Suppressions are explicit contracts: suppressed JPS-derived deps must not produce validation errors.
- Only `LibraryModuleValidator` and `TestLibraryScopeValidator` read `.iml` dependencies directly; their scope is still anchored to graph content modules.

## Index

| Validator | Entry point | NodeId | Spec |
| --- | --- | --- | --- |
| Plugin content dependencies | `PluginContentDependencyValidator` | `pluginValidation` | [plugin-content-dependency.md](plugin-content-dependency.md) |
| Plugin content structural validation | `PluginContentStructureValidator` | `pluginContentStructureValidation` | [plugin-content-structure.md](plugin-content-structure.md) |
| Plugin-to-plugin dependencies | `PluginPluginDependencyValidator` | `pluginPluginValidation` | [plugin-plugin-dependency.md](plugin-plugin-dependency.md) |
| Plugin dependency declaration duplicates | `PluginDependencyDeclarationValidator` | `pluginDependencyDeclarationValidation` | [plugin-dependency-declaration.md](plugin-dependency-declaration.md) |
| Test plugin plugin dependencies | `TestPluginPluginDependencyValidator` | `testPluginPluginDependencyValidation` | [test-plugin-plugin-dependency.md](test-plugin-plugin-dependency.md) |
| Content module dependencies (bundled plugins) | `ContentModuleDependencyValidator` | `pluginContentModuleValidation` | [content-module-dependency.md](content-module-dependency.md) |
| Duplicate plugin content modules | `PluginContentDuplicatesValidator` | `pluginContentDuplicateValidation` | [plugin-content-duplicates.md](plugin-content-duplicates.md) |
| Test plugin descriptor ID conflicts | `PluginDescriptorIdConflictValidator` | `pluginDescriptorIdConflictValidation` | [plugin-descriptor-id-conflicts.md](plugin-descriptor-id-conflicts.md) |
| Product module set validation | `ProductModuleSetValidator` | `productModuleSetValidation` | [product-module-set.md](product-module-set.md) |
| Self-contained module set validation | `SelfContainedModuleSetValidator` | `selfContainedValidation` | [self-contained-module-set.md](self-contained-module-set.md) |
| Library module replacement | `LibraryModuleValidator` | `libraryModuleValidation` | [library-module.md](library-module.md) |
| Test library scope | `TestLibraryScopeValidator` | `testLibraryScopeValidation` | [test-library-scope.md](test-library-scope.md) |
| Suppression config keys | `SuppressionConfigValidator` | `suppressionConfigValidation` | [suppression-config.md](suppression-config.md) |

## Related docs

- [validation-rules.md](../validation-rules.md)
- [dependency_generation.md](../dependency_generation.md)
- [errors.md](../errors.md)
