# Dependency Generation

This document describes how IntelliJ's build system generates module dependencies in XML descriptor files.

## TL;DR - The Core Mental Model

**Question**: Which JPS (`.iml`) dependencies become `<module>` entries in XML?

**Answer**: Only those with **PRODUCTION_RUNTIME** scope.

| JPS Scope | In PRODUCTION_RUNTIME? | → XML? | Why |
|-----------|------------------------|--------|-----|
| COMPILE   | ✓ Yes                  | ✓ Yes  | Code needed at compile AND runtime |
| RUNTIME   | ✓ Yes                  | ✓ Yes  | Runtime-only dependencies |
| PROVIDED  | ✗ No                   | ✗ No   | Provided by runtime environment |
| TEST      | ✗ No                   | ✗ No   | Test code only |

**Data Flow**:
```
.iml files → Graph (all edges + scope) → Filter (PRODUCTION_RUNTIME) → XML output
```

## Graph Is the Source of Truth

All dependency generation and validation must use **PluginGraph** as the single source of truth. Avoid re-parsing plugin.xml or reading module/product descriptors from disk to determine dependencies or "pseudo-core" plugins; product DSL and module sets already populate the graph.

- Use graph edges created from JPS dependencies and DSL content.
- For JPS **library** dependencies, resolve library name -> library module via `ModuleSetGenerationConfig.projectLibraryToModuleMap` (built from JPS library modules), not by scanning `.idea` libraries or module libraries.
- The graph already encodes product/module-set aliases and pseudo-core plugins; do not build parallel maps.
- Keep generator and validator in sync: if validation expects a dependency to be present, generator must be able to emit it (or mark it implicit/suppressed) so a second run is clean.
- Suppression config is a contract: if a JPS-derived dep is suppressed, it is intentionally omitted from XML and must not produce validation errors. Validation should only consider deps represented in the graph after filtering/suppression.

### Graph Completeness During DSL Test Plugin Expansion

The graph is still *incomplete* when `computePluginContentFromDslSpec` runs:
- It already contains products, module sets, and plugin.xml content from extracted plugins.
- It does **not** yet contain JPS-only dependencies discovered during the DSL test plugin auto-add BFS.

To keep auto-add decisions graph-driven, model building pre-marks all JPS targets that have
`{moduleName}.xml` descriptors on disk. This descriptor-presence flag is stored on content module nodes
and used by DSL test plugin expansion to decide which JPS deps are eligible for auto-add, without
additional disk I/O during generation. These modules are registered in the graph later when the DSL test
plugin content is added via `addPluginWithContent`.

**Invariant:** `markDescriptorModules()` must run after the last graph mutation before
`computePluginContentFromDslSpec` executes. The graph snapshot carries a `descriptorFlagsComplete` flag,
and DSL test plugin expansion fails fast if the flag is not set.

Validation behavior is specified in [docs/validators/README.md](validators/README.md).

**Key Functions**:
- `PluginGraphBuilder.addJpsDependencies()` - stores ALL deps with scope as graph edges
- `ContentModuleDependencyPlanner.computeJpsDeps()` - filters to production scopes
- `collectPluginGraphDeps()` + `filterPluginDependencies()` (PluginDependencyPlanner) - plugin.xml filtering based on graph

### Why Exclude PROVIDED?

PROVIDED means "provided by the runtime environment":
- Platform APIs (already loaded by IDE core)
- JDK classes (always available)
- Servlet APIs (provided by web container)

These are needed at compile time for type checking, but the classes come from
the environment at runtime, not from the dependency module.

### Why Exclude TEST?

TEST scope dependencies are only for test code execution. Production runtime
doesn't need them. Test descriptors (`._test.xml`) handle test deps separately.

**Source**: `JpsJavaDependencyScope.java:23-26` defines which scopes include `PRODUCTION_RUNTIME`.

---

## How to Run

See [quick-start.md](quick-start.md#run-commands) for running the generator.

---

## Architecture Overview

The dependency generation system uses a **5-stage pipeline architecture** with slot-based `ComputeNode` execution. For the complete architecture diagram and system context, see [architecture-overview.md](architecture-overview.md).

**Key optimizations:**
- Plugin content extraction is shared via `PluginContentCache` (pre-warmed in Stage 2)
- Generators run in parallel at each "level" (computed via topological sort of dependencies)
- `DeferredFileUpdater` batches all writes for atomic commit

## Plugin Validation Architecture

Validation behavior is specified in [docs/validators/README.md](validators/README.md). This section provides generation-facing context only.

Plugin dependency validation queries **PluginGraph** directly; no parallel resolution map is built.

### Data Flow

```
PluginGraph
   │
   ▼
createResolutionQuery() (PluginDependencyResolution.kt)
   │
   ▼
PluginContentStructureValidator (PluginContentStructureValidator.kt):
  └── Structural validation (loading-mode constraints within a plugin)

PluginContentDependencyValidator (PluginContentDependencyValidator.kt):
  ├── Availability validation (deps in bundling products)
  ├── Global existence (ON_DEMAND deps exist somewhere)
  └── Filtered dependency validation (implicit deps not in XML)
```

### Validation Scopes by Plugin Type

| Plugin Type | Bundling Source | REQUIRED Deps Scope | ON_DEMAND Deps |
|-------------|-----------------|---------------------|----------------|
| Production bundled | Graph `EDGE_BUNDLES` | product + bundled plugin modules | Global existence |
| Test bundled | Graph `EDGE_BUNDLES_TEST` | product + bundled plugin modules | Global existence |
| Non-bundled | (none) | N/A | Global existence |

**Key insight**: Test plugins rely on graph flags and bundling edges, so validation doesn't need a separate product map.

### Source Files

| Component | File |
|-----------|------|
| Plugin validation model building | `src/validator/rule/PluginDependencyResolution.kt` |
| Plugin validation logic | `src/validator/PluginContentDependencyValidator.kt` |
| Plugin structural validation | `src/validator/PluginContentStructureValidator.kt` |
| Content module plugin dep validation | `src/validator/ContentModulePluginDependencyValidator.kt` |
| Plugin dependency planning + XML writing | `src/generator/ContentModuleDependencyGenerator.kt`, `src/generator/ContentModuleXmlWriter.kt`, `src/generator/PluginXmlDependencyGenerator.kt`, `src/generator/PluginXmlWriter.kt` |
| Data models | `src/validation/ValidationModels.kt` |

### Content Module Plugin Dependency Validation

A specialized validation rule ensures content modules properly declare plugin dependencies.

**Problem**: When `intellij.foo` (content module) has IML dependency on `intellij.python.community.plugin` (plugin main module), it needs `<plugin id="PythonCore"/>` in its XML. Without this, runtime fails with `NoClassDefFoundError`.

**Solution**: `ContentModulePluginDependencyValidator` validates:
1. Resolve containing plugins via `contentProductionSources` in the graph
2. Check if plugin ID exists in written XML deps
3. Report missing, with suppression config option

This handles many-to-many content module → plugin relationships directly from the graph.

**Suppressing Known Issues**: Use `contentModuleAllowedMissingPluginDeps` in `ModuleSetGenerationConfig`:
```kotlin
ModuleSetGenerationConfig(
  contentModuleAllowedMissingPluginDeps = mapOf(
    "intellij.react.ultimate" to setOf("com.intellij.css"),
  ),
)
```

See [docs/validators/plugin-content-dependency.md](validators/plugin-content-dependency.md) for details.

## Key Components

### ModuleDescriptorDependencyGenerator.kt
Generates dependencies for **product modules** (modules declared in module sets like `essential()`, `ideCommon()`).

**Responsibilities:**
- Collects modules with `includeDependencies=true`, library modules (`intellij.libraries.*`), settings modules
- Validates both direct AND transitive dependencies
- Performs two-tier validation:
  - **Tier 1:** Self-contained module sets validated in isolation
  - **Tier 2:** Product-level dependencies validated in context

**Files updated:** `{moduleName}.xml` in `META-INF/`

### ContentModuleDependencyPlanner + ContentModuleXmlWriter
Generates dependencies for **plugin content modules** (modules declared in plugin.xml `<content>` sections).

**Responsibilities:**
- Plans dependencies for production and test content modules (including `._test` modules)
- Writes `{moduleName}.xml` and `{moduleName}._test.xml` descriptors
- Handles content modules ending with `._test` (their `.xml` IS the test descriptor)

**Files updated:** content module XMLs, test descriptor XMLs

### PluginDependencyPlanner + PluginXmlWriter
Generates dependencies for **plugin.xml** of plugin main modules.

**Responsibilities:**
- Processes `plugin.xml` - main plugin descriptor
- Writes plugin-level dependency entries derived from graph/JPS deps

**Files updated:** `plugin.xml`

### ModuleDescriptorCache.kt
Async-safe caching layer for module descriptor information.

**Features:**
- Deferred-based pattern: first caller creates async job, subsequent callers await same result
- Caches: descriptor path, dependencies list, XML content
- Filters dependencies to include only those that have descriptors

### ProductGeneration.kt
Orchestrates all generation via `generateAllModuleSetsWithProducts()`.

**Configuration via `ModuleSetGenerationConfig`:**
```kotlin
data class ModuleSetGenerationConfig(
  val moduleSetSources: Map<String, Pair<Any, Path>>,  // label → (source object, output dir)
  val discoveredProducts: List<DiscoveredProduct>,
  val testProductSpecs: List<Pair<String, ProductModulesContentSpec>>,
  val projectRoot: Path,
  val outputProvider: ModuleOutputProvider,
  val additionalPlugins: Map<String, String>,
  val dependencyFilter: (embeddedModules, moduleName, depName, isTest) -> Boolean,
  val skipXIncludePaths: Set<String>,
  val xIncludePrefixFilter: (moduleName) -> String?,
  val testFrameworkContentModules: Set<String>,  // Modules indicating test plugins
)
```

**`testFrameworkContentModules`** - Modules that indicate a plugin is a test plugin when declared as `<content>`. Plugins declaring any of these modules are excluded from production validation because they won't be present at runtime. See [validation-rules.md](validation-rules.md) for details.

## JPS Scopes vs Plugin Model

The plugin model has **no concept of TEST/COMPILE/RUNTIME scopes** - it only knows about runtime dependencies. JPS `.iml` files DO have scopes, which affects dependency generation. See [TL;DR](#tldr---the-core-mental-model) for the scope filtering rules.

### Dual Edge Types for Production vs Test

The generator computes **both** production and test dependencies for each content module:

| Edge Type | JPS Scopes Included | Written to XML | Use Case |
|-----------|---------------------|----------------|----------|
| `EDGE_CONTENT_MODULE_DEPENDS_ON` | COMPILE, RUNTIME | Yes | Production validation |
| `EDGE_CONTENT_MODULE_DEPENDS_ON_TEST` | COMPILE, RUNTIME, TEST | No | Test plugin validation |

**Key insight**: Content modules are production code with intrinsic dependencies. A content module's production deps are the same regardless of which plugin includes it.

### Example

```
intellij.platform.lang.iml has:
  - intellij.platform.core (COMPILE scope)
  - intellij.libraries.hamcrest (TEST scope)

Graph edges created:
  EDGE_CONTENT_MODULE_DEPENDS_ON: lang -> core
  EDGE_CONTENT_MODULE_DEPENDS_ON_TEST: lang -> core, lang -> hamcrest

Production validation: Only checks EDGE_CONTENT_MODULE_DEPENDS_ON
  → Won't report hamcrest as missing

Test plugin validation: Checks EDGE_CONTENT_MODULE_DEPENDS_ON_TEST
  → Will validate hamcrest is available
```

## Module Descriptor vs Plugin Dependencies

| Aspect | Module Descriptor Dependencies | Plugin Dependencies |
|--------|-------------------------------|---------------------|
| **Source** | Modules in module sets | Modules in plugin.xml `<content>` |
| **Generator** | `ModuleDescriptorDependencyGenerator` | `PluginDependencyPlanner` + `PluginXmlWriter` |
| **Files updated** | `{moduleName}.xml` | `plugin.xml`, content module XMLs |
| **Validation** | Full transitive validation | JPS dependencies with filtering |
| **Configuration** | `includeDependencies=true` flag | Automatic for all content modules |
| **Filtering** | None (use `@skip-dependency-generation` to skip) | `dependencyFilter` applied |

### Plugin.xml Generation Scope

Plugin XML dependencies are generated only for plugins that have a main target in the graph
(real plugin modules extracted from disk or discovered via dependencies). Placeholder plugin-id
nodes created only to model `<depends>` edges are skipped.

DSL-defined plugins (`testPlugin {}`) are generated from Kotlin specs; `PluginXmlWriter`
does not update their plugin.xml files (handled by `TestPluginXmlGenerator`).

Dependencies are computed from the graph's JPS edges (production-runtime scopes only); plugin.xml
content is read only to preserve manual entries and xi:include content.

## The `dependencyFilter` Function

Determines which dependencies should be included in generated XML files.

**Signature:**
```kotlin
(embeddedModules: Set<String>, moduleName: String, depName: String, isTest: Boolean) -> Boolean
```

**Parameters:**
- `embeddedModules` - modules with `loading="embedded"` in the product
- `moduleName` - module whose dependencies are being processed
- `depName` - candidate dependency module name
- `isTest` - whether this is for a test descriptor or production

**Example from ultimateGenerator.kt:**
```kotlin
dependencyFilter = { embeddedModules, moduleName, depName, isTest ->
  // For production descriptors, skip test-related modules
  if (!isTest && (
    moduleName.startsWith("intellij.platform.testFramework") ||
    moduleName == "intellij.tools.testsBootstrap"
  )) {
    return@ModuleSetGenerationConfig false
  }

  // Only include library modules if not embedded
  if (depName.startsWith(LIB_MODULE_PREFIX)) {
    !embeddedModules.contains(depName)
  } else {
    false
  }
}
```

**Important:** The `dependencyFilter` only applies to **plugin content modules** processed by `PluginDependencyPlanner`.
It does NOT apply to **module set modules** (including library modules) processed by `ModuleDescriptorDependencyGenerator`.

### Implicit Dependencies Tracking

**Key behavior:** JPS dependencies that survive filtering and suppression are **validated**, but only filtered-in dependencies are **written to XML**.

When `dependencyFilter` returns `false` for a dependency:
1. The dependency is NOT written to the XML descriptor
2. The dependency IS still validated unless it is suppressed by `suppressions.json` or allowlists
3. The dependency is tracked as "implicit" (computed: `allJpsDependencies - writtenDependencies`)
4. If validation fails, error messages show "(auto-inferred JPS dependency, filtered by config)"

**DependencyFileResult structure:**
```kotlin
data class DependencyFileResult(
  val writtenDependencies: List<String>,    // deps written to XML
  val allJpsDependencies: Set<String>,      // all JPS deps (superset)
) {
  val implicitDependencies: Set<String>     // computed: allJps - written
    get() = allJpsDependencies - writtenDependencies.toSet()
}
```

This design ensures that:
- Validation catches missing dependencies that are not suppressed (not just those written to XML)
- Error messages clearly distinguish between explicit and implicit dependencies
- Users know to add missing deps to `pluginAllowedMissingDependencies` rather than the XML

**Data flow:**
```
JPS Dependencies (.iml)
        │
        ▼
dependencyFilter() ──┬── true  → writtenDependencies (XML) + Validated
                     │
                     └── false → implicitDependencies (NOT in XML) + Validated unless suppressed
                                 Error shows "(auto-inferred JPS dependency, filtered by config)"
```

See [errors.md](errors.md) for error handling details.

## Globally Embedded Module Filtering

Modules that are **globally embedded** are automatically skipped when generating dependencies. This reduces XML bloat and eliminates the need for manual suppressions.

### Definition

A module is **globally embedded** if ALL of these conditions are true:
1. The module **is contained** by at least one product or module set (non-plugin content source)
2. The module has **EMBEDDED loading** in ALL product/module-set sources

Plugin content sources do not disqualify a module from being globally embedded.

### Why Skip Globally Embedded Modules?

Globally embedded modules are **always loaded** with the product. They're part of the core platform and don't need explicit XML dependencies because:
- They're available at runtime without declaring a dependency
- They can't be disabled or unloaded
- Declaring them adds no value but creates maintenance burden

### Examples

| Module | Plugin Source? | Loading Mode | Globally Embedded? |
|--------|----------------|--------------|-------------------|
| `intellij.platform.core` | No | EMBEDDED in `essential` | ✓ Yes - skip |
| `intellij.libraries.ktor.client` | No | EMBEDDED in `essential.minimal` | ✓ Yes - skip |
| `intellij.vcs.impl` | Yes (`vcs` plugin) | - | ✗ No - keep |
| `intellij.platform.optional` | No | REQUIRED in `optional.set` | ✗ No - keep |

### Scope

This filtering applies to:
- **Plugin XML dependencies** (`<dependencies><module>` in plugin.xml)
- **Content module dependencies** (only for content modules **in plugins**, not directly in products)

Content modules directly in products (via module sets) do NOT skip embedded deps because they're not "inside a plugin" - they're at the product level where the embedment relationship is defined. If a module is present both in a plugin and a module set, treat it as product content and do not skip embedded deps.

### Implementation

The filtering is implemented in:
- `EmbeddedModuleUtils.kt` - shared utility functions
- `collectPluginGraphDeps()` + `filterPluginDependencies()` (PluginDependencyPlanner) - plugin.xml filtering
- `ContentModuleDependencyPlanner.computeJpsDeps()` - content module filtering

**Key functions:**
```kotlin
// Check if module has any plugin as content source
fun GraphScope.hasPluginSource(moduleId: Int): Boolean

// Check if module is globally embedded
fun GraphScope.isGloballyEmbedded(moduleId: Int): Boolean
```

## Skipping Dependency Generation for Module Set Modules

For module set modules (including library modules like `intellij.libraries.*`), the `dependencyFilter` is NOT applied.
All JPS dependencies with descriptors are included automatically.

If a module requires **manual dependency management** (e.g., for specific ordering requirements),
add the `@skip-dependency-generation` comment to the module descriptor XML file:

```xml
<idea-plugin visibility="internal">
  <!-- @skip-dependency-generation - reason for manual management -->
  <dependencies>
    <!-- manually managed dependencies -->
  </dependencies>
</idea-plugin>
```

**Use cases:**
- Dependencies requiring specific topological sort ordering (e.g., `intellij.libraries.junit5.jupiter`)
- Modules with complex dependency requirements not expressible via JPS

When this marker is present, the module is completely skipped by `ModuleDescriptorDependencyGenerator`,
preserving all manually specified dependencies.

## Test Plugins

Test plugins are special plugins that provide test framework modules for running tests.
Unlike regular products, test plugins have plugin.xml in test resources (`testResources/META-INF/`).

### Test Plugin Detection

Plugins extracted from plugin.xml are detected as **test plugins** based on their content modules. DSL-defined test plugins (`testPlugin {}`) are always treated as test plugins even if they don't declare test framework modules. A plugin is a test plugin if it declares any test framework module in its `<content>` block:

```kotlin
testFrameworkContentModules = setOf(
  "intellij.libraries.junit4",
  "intellij.libraries.junit5",
  "intellij.libraries.junit5.jupiter",
  "intellij.platform.testFramework",
  "intellij.platform.testFramework.core",
  "intellij.tools.testsBootstrap",
)
```

**Key implications**:
- Test plugins' content modules do NOT satisfy production plugin dependencies
- Test plugins use graph bundling edges (`EDGE_BUNDLES_TEST`) instead of a separate product map
- Discovered test plugins use `forTestPlugin` (module sets + all bundled plugins); DSL test plugins use `forDslTestPlugin` (module sets + bundled production plugins + self)

### DSL-Defined vs Discovered Test Plugins

| Type | Definition | Auto-fix behavior |
|------|------------|-------------------|
| **DSL-defined** | Created via `testPlugin {}` in `getProductContentDescriptor()` | Skipped - fix in Kotlin |
| **Discovered** | Manually created with handwritten plugin.xml | Auto-fixes can be applied |

### Auto-Add Behavior for DSL Test Plugins

For DSL-defined test plugins, the generator can **automatically add** JPS module dependencies (production runtime, test runtime, and PROVIDED scopes) that have module descriptors but weren't explicitly declared.

**Key Principle**: Only add **unresolvable** modules - those not available in the same product (module sets + bundled production plugins; other test plugins excluded).

```
JPS Dependencies (.iml)
        │
        ▼
Check: Has module descriptor?
        │
        ├── NO  → Skip (not a content module)
        │
        └── YES → Check: Is module resolvable?
                        │
                        ├── YES (in product module set/bundled production plugin content) → Skip
                        │
                        └── NO (unresolvable) → Auto-add to test plugin content
```

Auto-add uses the **graph** to check resolvable modules, but traverses **JPS dependencies** of the
explicit content modules. Project library dependencies resolve via `ModuleSetGenerationConfig.projectLibraryToModuleMap`
(built from JPS library modules, not graph targets), and `libraryModuleFilter` is ignored for DSL test plugins
because the test plugin must be a complete container in dev mode. Auto-added modules are written into the
generated test plugin content (the `<!-- region additional -->` block), so repeat runs are clean.

**Why this design**: Module sets are just convenience for avoiding duplication - they're NOT special. The auto-add logic respects them naturally without special handling.

### Key Differences from Products

| Aspect | Products | Test Plugins |
|--------|----------|--------------|
| plugin.xml location | `resources/META-INF/` | `testResources/META-INF/` |
| Module set handling | xi:include or inline | Always inlined |
| Dependency resolution | `forProductionPlugin` predicate | `forTestPlugin` predicate |
| Content modules | Satisfy other plugin deps | Don't satisfy production deps |

For DSL reference, see [dsl-api-reference.md](dsl-api-reference.md#testplugin----define-test-plugin).

## Test Descriptor Handling (`._test.xml`)

Test descriptors provide dependencies for test code.

**Two cases:**

1. **Regular content modules** (e.g., `intellij.foo`):
   - Production descriptor: `intellij.foo.xml`
   - Test descriptor: `intellij.foo._test.xml`
   - Both are processed; test uses `withTests=true` for JPS dependencies

2. **Test content modules** (e.g., `intellij.foo._test`):
   - Their `.xml` file IS the test descriptor
   - No separate `._test._test.xml` file
   - Processed with `isTest=true` filter

**Code logic:**
```kotlin
val isTestModule = contentModuleName.endsWith("._test")
generateContentModuleDependencies(
  dependencyFilter = { dependencyFilter(moduleName, it, isTestModule) }
)
if (!isTestModule) {
  generateTestContentModuleDependencies(...)  // Process ._test.xml
}
```

## XML Generation Format

Dependencies are written within region markers:
```xml
<dependencies>
  <!-- region Generated dependencies - run `Generate Product Layouts` to regenerate -->
  <module name="intellij.libraries.grpc"/>
  <module name="intellij.platform.kernel"/>
  <!-- endregion -->
</dependencies>
```

**Region types:**
- `WRAPS_ENTIRE_SECTION` - module descriptors (region wraps whole `<dependencies>`)
- `INSIDE_SECTION` - plugin.xml (region inside, preserves manual entries)
- `NONE` - legacy files without markers

## Validation

Validation is implemented by pipeline validators under `src/validator/`. See [docs/validators/README.md](validators/README.md) for the authoritative specs. This document focuses on dependency generation only.

## Suppression Config System

The suppression config system provides a **JSON-based single source of truth** for dependency suppressions.

**Location:** `platform/buildScripts/suppressions.json`

**Purpose:** When JPS dependencies shouldn't be written to XML descriptors (e.g., legacy deps that were manually managed), the suppression config tracks these exclusions. This is an explicit contract for incremental cleanup: suppressed deps are intentionally omitted from XML and must not trigger validation errors.

Validation is graph-based: only dependencies represented after filtering/suppression are validated; suppressed JPS deps are excluded by design.

For detailed implementation documentation, see `SuppressionConfigGenerator.kt`.

### Config Structure

```json
{
  "contentModules": {
    "intellij.foo": {
      "suppressModules": ["intellij.bar"],
      "suppressPlugins": ["com.intellij.java"]
    }
  },
  "plugins": {
    "intellij.cidr.clangd": {
      "suppressModules": ["intellij.platform.core"],
      "suppressPluginModules": []
    }
  }
}
```

| Field | Purpose |
|-------|---------|
| `contentModules[].suppressModules` | Module deps to suppress from content module XMLs |
| `contentModules[].suppressPlugins` | Plugin deps to suppress from content module `<depends>` |
| `plugins[].suppressModules` | Module deps to suppress from plugin.xml `<dependencies>` |
| `plugins[].suppressPluginModules` | Plugin module deps to suppress from plugin.xml |

### Running the Generator

```bash
# Run generator - automatically updates suppressions.json if needed
bazel run //platform/buildScripts:plugin-model-tool

# Review and commit changes
git diff platform/buildScripts/suppressions.json
```

**Key principle:** The generator should produce ZERO changes when run twice.

See [errors.md](errors.md#suppressible-errors) for details on direct error suppression.

## Source locations

- Pipeline and slots: `src/pipeline/`
- Generators: `src/pipeline/generators/`
- Validators: `src/validator/`
- Validator rules and models: `src/validator/rule/`, `src/model/`
- Discovery and graph building: `src/discovery/`
- Traversal helpers: `src/traversal/`
- Tooling (MCP server): `src/tooling/`
- Entry points: `platform/buildScripts/src/productLayout/` and `src/` (CommunityModuleSets)

All paths in `src/` are relative to `community/platform/build-scripts/product-dsl/`.
