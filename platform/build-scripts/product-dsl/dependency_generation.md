# Dependency Generation System

This document describes how IntelliJ's build system generates module dependencies in XML descriptor files.

## Overview

The dependency generation system is a **four-tier orchestrated pipeline** that:
1. Generates module set XML files from DSL definitions
2. Updates module descriptor dependencies for product modules
3. Updates plugin.xml dependencies for bundled plugins and their content modules
4. Generates product XML files from specifications

All tiers run **concurrently** using Kotlin coroutines for performance.

## How to Run

- **IDE:** Run configuration `Generate Product Layouts`
- **Bazel:** `bazel run //platform/buildScripts:plugin-model-tool`
- **CLI flags:** `--json` outputs model analysis; `--json='{"filter":"products"}'` for specific sections

## Architecture

```
generateAllModuleSetsWithProducts (ProductGeneration.kt)
  │
  ├─ TIER 1: Module Set Generation (doGenerateAllModuleSetsInternal)
  │         Discovers module sets via reflection, generates XML files
  │
  ├─ TIER 2: Module Descriptor Dependencies (generateModuleDescriptorDependencies)
  │         Processes product modules with transitive validation
  │
  ├─ TIER 3: Plugin Dependencies (generatePluginDependencies)
  │         Processes plugin.xml and content modules (including ._test.xml)
  │
  └─ TIER 4: Product XML Generation (generateAllProductXmlFiles)
            Generates complete plugin.xml files from product specs
```

**Key optimization:** Plugin content extraction (xi:include resolution) is shared via `Deferred` jobs across TIER 2 and TIER 3 to avoid duplicate work.

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

### PluginDependencyGenerator.kt
Generates dependencies for **plugin content modules** (modules declared in plugin.xml `<content>` sections).

**Responsibilities:**
- Processes `plugin.xml` - main plugin descriptor
- Processes `{moduleName}.xml` - production content module descriptors
- Processes `{moduleName}._test.xml` - test content module descriptors
- Handles content modules ending with `._test` (their `.xml` IS the test descriptor)

**Files updated:** `plugin.xml`, content module XMLs, test descriptor XMLs

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

**`testFrameworkContentModules`** - Modules that indicate a plugin is a test plugin when declared as `<content>`. Plugins declaring any of these modules are excluded from production validation because they won't be present at runtime. See [Test Plugin Detection](../validation.md#test-plugin-detection) for details.

## Module Descriptor vs Plugin Dependencies

| Aspect | Module Descriptor Dependencies | Plugin Dependencies |
|--------|-------------------------------|---------------------|
| **Source** | Modules in module sets | Modules in plugin.xml `<content>` |
| **Generator** | `ModuleDescriptorDependencyGenerator` | `PluginDependencyGenerator` |
| **Files updated** | `{moduleName}.xml` | `plugin.xml`, content module XMLs |
| **Validation** | Full transitive validation | JPS dependencies with filtering |
| **Configuration** | `includeDependencies=true` flag | Automatic for all content modules |
| **Filtering** | None (use `@skip-dependency-generation` to skip) | `dependencyFilter` applied |

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

**Important:** The `dependencyFilter` only applies to **plugin content modules** processed by `PluginDependencyGenerator`.
It does NOT apply to **module set modules** (including library modules) processed by `ModuleDescriptorDependencyGenerator`.

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

The system performs multi-tier validation via the `validation/` package:

**Validation Rules** (in `validation/rules/`):
- **DependencyValidation** - Module dependencies must be reachable within module set hierarchy
- **LibraryModuleValidation** - Modules must not depend on libraries exported by library modules; they should depend on the library module directly
- **LocationValidation** - Community products must not use ultimate modules; module sets should be in correct locations

**Example bug caught:**
- `intellij.platform.kernel` (in `corePlatform()`) depends on `fleet.kernel`
- `corePlatform()` doesn't include `fleet()` module set
- **Result:** Validation error prevents runtime `NoClassDefFoundError`

Validation errors are reported with full paths showing which product/module set is affected. Error types are defined in `ValidationModels.kt`, formatting in `ValidationFormatters.kt`.

## Source Files

| File | Location |
|------|----------|
| **Dependency Generation** | |
| ModuleDescriptorDependencyGenerator | `src/dependency/` |
| PluginDependencyGenerator | `src/dependency/` |
| ModuleDescriptorCache | `src/dependency/` |
| **Validation** | |
| ValidationModels | `src/validation/` |
| ValidationFormatters | `src/validation/` |
| DependencyValidation | `src/validation/rules/` |
| LibraryModuleValidation | `src/validation/rules/` |
| LocationValidation | `src/validation/rules/` |
| **Traversal** | |
| ModuleSetTraversal | `src/traversal/` |
| ModuleSetTraversalCache | `src/traversal/` |
| ModulePathAnalysis | `src/traversal/` |
| ModuleDependencyAnalysis | `src/traversal/` |
| **Discovery** | |
| ProductGeneration | `src/discovery/` |
| ProductDiscovery | `src/discovery/` |
| ModuleSetDiscovery | `src/discovery/` |
| PluginContentExtractor | `src/discovery/` |
| **Tooling (MCP Server)** | |
| AnalysisModels | `src/tooling/` |
| SimilarityAnalysis | `src/tooling/` |
| UnificationAnalysis | `src/tooling/` |
| **Entry Points** | |
| UltimateGenerator | `platform/buildScripts/src/productLayout/` |
| CommunityModuleSets | `src/` |
| UltimateModuleSets | `platform/buildScripts/src/productLayout/` |

All paths in `src/` are relative to `community/platform/build-scripts/product-dsl/`.
