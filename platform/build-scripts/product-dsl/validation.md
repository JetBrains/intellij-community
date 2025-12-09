# Plugin Model Validation

This document describes the validation system that ensures module dependencies are resolvable at build time, preventing runtime errors.

## Overview

### Why Validation Exists

Without validation, dependency errors only surface at runtime:
```
Plugin 'Java' has dependency on 'com.intellij.modules.vcs' which is not installed
```

The validation system catches these errors during `Generate Product Layouts`, providing:
- Early detection of missing dependencies
- Clear error messages with suggested fixes
- Module set hierarchy awareness
- Cross-plugin dependency support

### When Validation Runs

Validation runs automatically when you execute:
- **IDE**: Run configuration "Generate Product Layouts"
- **CLI**: `UltimateModuleSets.main()` or `CommunityModuleSets.main()`
- **Bazel**: `bazel run //platform/buildScripts:product-model-tool`

## Two-Tier Validation System

The build system uses a **two-tier validation approach** to ensure module dependencies are resolvable while avoiding false positives.

### Tier 1: Product-Level Validation

**What it validates**: All products with their complete module composition

**How it works**:
- Collects all modules from a product's complete module set hierarchy
- Validates that each module's dependencies are available within that product context
- Includes bundled plugin content modules in available modules
- Reports errors per-product with clear affected modules

**Why this tier exists**: Products are the actual deployment units. Most module sets (like `debugger()`, `vcs()`, `xml()`) are **composable building blocks** designed to work together. Validating them in isolation would produce false positives because they intentionally depend on modules from other sets.

**Example**:
- Product `GoLand` uses: `ide.ultimate`, `ssh`, `rd.common`
- Validation checks: Can all modules in `ide.ultimate` + `ssh` + `rd.common` resolve their dependencies within this combined set?

### Tier 2: Self-Contained Module Set Validation

**What it validates**: Module sets marked with `selfContained = true`

**How it works**:
- Validates the module set in isolation without considering other module sets
- Flattens the entire hierarchy (nested sets all see each other)
- Ensures all dependencies are resolvable within the set itself
- Reports errors specific to that module set

**Why this tier exists**: Some module sets are designed to be **standalone/self-contained** and used directly by products without composition with other sets. These sets must have all their dependencies available internally.

**When to use `selfContained = true`**:
- Module set is used directly by products as the primary/only module set
- Module set represents a complete, independent runtime environment
- You want to enforce that the set doesn't leak dependencies on external sets

**Example**: `core.platform` is self-contained because CodeServer uses it alone.

```kotlin
fun corePlatform(): ModuleSet = moduleSet(
  name = "core.platform",
  selfContained = true,  // Must be resolvable in isolation
  outputModule = "intellij.platform.ide.core"
) {
  moduleSet(librariesPlatform())
  moduleSet(rpcMinimal())
  embeddedModule("intellij.platform.core", includeDependencies = true)
  // ...
}
```

## Cross-Plugin Dependency Validation

### The Problem

Bundled plugins can have content modules that optionally depend on modules from non-bundled plugins. For example:

- `intellij.fullLine.cpp` (bundled in IDEA Ultimate) depends on `intellij.c.core`
- `intellij.c.core` is part of the C/C++ plugin (`intellij.c`), which is **not bundled**

Without special handling, this triggers a validation error because `intellij.c.core` is not in the product's module sets.

### Loading Attribute Semantics

The solution depends on the content module's **loading attribute**:

| Loading Value | Meaning | Cross-Plugin Deps Allowed? |
|---------------|---------|---------------------------|
| `embedded` | Core module, loaded into main classloader | **No** - must have all deps in product |
| `required` | Required at startup, loaded early | **No** - must have all deps in product |
| `optional` | Loaded on demand if deps available | **Yes** - can depend on non-bundled plugins |
| `on_demand` | Loaded only when explicitly requested | **Yes** - can depend on non-bundled plugins |
| (unspecified) | Default loading behavior | **Yes** - can depend on non-bundled plugins |

**Key insight**: Critical modules (`embedded`/`required`) cannot depend on non-bundled plugins because those plugins may not be installed. Non-critical modules can safely have cross-plugin dependencies since they won't prevent IDE startup if the dependency is missing.

### Configuration

Non-bundled plugins for validation are configured in `ultimateGenerator.kt` via `additionalPlugins`:

```kotlin
generateAllModuleSetsWithProducts(
  ModuleSetGenerationConfig(
    // ...
    additionalPlugins = loadNonBundledPluginsForValidation(projectRoot) + listOf(
      "intellij.c",  // C/C++ support - needed by intellij.fullLine.cpp
      "kotlin.frontend.split",
    ),
  )
)
```

The `loadNonBundledPluginsForValidation()` function loads plugins from expected content YAML files (e.g., `ultimate-content-platform.yaml`, `rider-content.yaml`).

### Adding a New Non-Bundled Plugin

When you encounter a validation error for a cross-plugin dependency:

1. **Verify the source module is NOT critical** (not `embedded` or `required`)
2. **Find the plugin module name** (not the plugin ID):
   ```bash
   # Find the .iml file for the plugin
   find . -name "*.iml" | xargs grep -l "plugin-descriptor-xml"
   ```
3. **Add to `additionalPlugins`** in `ultimateGenerator.kt`:
   ```kotlin
   additionalPlugins = loadNonBundledPluginsForValidation(projectRoot) + listOf(
     "intellij.c",
     "intellij.new.plugin",  // Description of why it's needed
   )
   ```

### How It Works Internally

1. **Plugin content extraction**: The generator uses `extractPluginContent()` to read each plugin's content modules from its `plugin.xml`

2. **All plugin modules collection**: At validation start, all plugin content modules (bundled + non-bundled from the config) are collected:
   ```kotlin
   val allPluginModules = mutableSetOf<String>()
   for ((_, job) in pluginContentJobs) {
     val info = job.await()
     if (info != null) {
       allPluginModules.addAll(info.contentModules)
     }
   }
   ```

3. **Validation with loading check**: For each module's dependencies:
   ```kotlin
   val loading = productIndex.moduleLoadings[moduleName]
   val isCritical = loading == ModuleLoadingRuleValue.EMBEDDED ||
                    loading == ModuleLoadingRuleValue.REQUIRED

   if (dep in reachableModules) {
     // Present in product - valid
   } else if (dep in allowedMissing) {
     // Explicitly allowed - skip
   } else if (dep in allPluginModules && !isCritical) {
     // Exists in another plugin AND source is NOT critical - valid
   } else {
     // Missing dependency - report error
   }
   ```

## allowMissingDependencies

### What It Is

Products can specify modules that are allowed to be missing from validation:

```kotlin
override fun getProductContentModules(): ProductModulesContentSpec = productModules {
  moduleSet(CommunityModuleSets.essential())
  // ...

  allowMissingDependencies = setOf(
    "intellij.some.optional.module"
  )
}
```

### When to Use

Use `allowMissingDependencies` sparingly for:
- **Modules provided by bundled plugins** that aren't declared as content modules
- **Temporary allowances** during migration (with a TODO to fix)
- **Platform quirks** where a dependency exists at runtime but not in module model

### When NOT to Use

Do NOT use for:
- **Cross-plugin dependencies** - use `additionalPlugins` in `ultimateGenerator.kt` instead
- **Avoiding validation errors** - fix the root cause
- **Optional features** - use proper loading attributes instead

### Best Practices

1. **Document each entry**: Add a comment explaining why it's needed
2. **Review periodically**: Remove entries that are no longer needed
3. **Prefer proper fixes**: `allowMissingDependencies` is a workaround, not a solution

## Performance Considerations

### Caching Strategy

The validation system uses multiple caching layers to minimize redundant work:

1. **ModuleDescriptorCache**: Module JPS dependencies are analyzed once per module and cached.
   - Eliminates redundant filesystem/JPS access
   - Thread-safe using double-checked locking with `moduleName.intern()`
   - Shared across all product validations

2. **ModuleSetTraversalCache**: Module set membership is computed once.
   - O(1) lookups instead of O(n) graph traversals
   - Caches: module sets by name, nested set closure, module names, loading modes
   - Thread-safe using `ConcurrentHashMap.computeIfAbsent()`

3. **ProductModuleIndex**: Per-product module composition built once before validation.
   - Collects all modules, loading modes, and source tracking
   - Enables parallel validation without redundant collection

### Per-Product vs Global Validation

Each product is validated separately because the "available modules" set differs:

```
Product A: modules from ideUltimate + ssh + rd.common
Product B: modules from ideCommunity + vcs
```

However, for **non-critical modules** (OPTIONAL, ON_DEMAND, unspecified loading), the effective "available" set is global:
- `crossProductModules` = union of ALL product modules
- `crossPluginModules` = all plugin content modules

This means non-critical modules validate against the same global set across products. The BFS traversal stops at cross-plugin/cross-product boundaries (doesn't traverse into external modules).

### Why Same Module May Be Validated Multiple Times

When module X is included in multiple products:
1. BFS traversal runs once per product
2. Module descriptor lookup is O(1) (cached)
3. Traversal uses in-memory data structures (fast)
4. Per-product context matters: `allowedMissingDependencies` differs

For non-critical plugin modules, a future optimization could pre-validate globally and reuse results per-product, only filtering by `allowedMissingDependencies`.

### Parallel Execution

Products are validated in parallel using `coroutineScope`:

```kotlin
return coroutineScope {
  productIndices.map { (productName, productIndex) ->
    async {
      validateSingleProduct(productIndex = productIndex, ...)
    }
  }.awaitAll().flatten()
}
```

This maximizes CPU utilization when validating many products.

## Troubleshooting

### Common Errors

#### 1. Missing Dependency in Product

```
❌ Product-level validation failed: Unresolvable module dependencies

Product: GoLand

  ✗ Missing: 'intellij.platform.polySymbols'
    Needed by: intellij.platform.vcs.impl
    Suggestion: Add module set: symbols
```

**Cause**: A module in the product depends on another module not available in the product's composition.

**Fix options**:
1. Add the suggested module set to the product
2. Add the individual module via `module()` or `embeddedModule()`
3. If it's a cross-plugin dependency with non-critical loading, add the plugin to `additionalPlugins` in `ultimateGenerator.kt`

#### 2. Self-Contained Set with Unresolvable Dependencies

```
❌ Module set 'core.platform' is marked selfContained but has unresolvable dependencies

  ✗ Missing: 'fleet.kernel'
    Needed by: intellij.platform.kernel

💡 To fix:
1. Add the missing modules/sets to 'core.platform' to make it truly self-contained
2. Or remove selfContained=true if this set is designed to compose with other sets
```

**Cause**: A self-contained module set doesn't have all its dependencies internally.

**Fix options**:
1. Add the missing module/module set
2. Remove `selfContained = true` if the set is meant to be composed with others

#### 3. Duplicate Content Modules

```
❌ Product 'GoLand' has duplicate content modules

Duplicated modules (appearing 2 times):
  ✗ intellij.platform.vcs.impl (appears 2 times)

💡 This causes runtime error: "Plugin has duplicated content modules declarations"
Fix: Remove duplicate moduleSet() nesting or redundant module() calls
```

**Cause**: The same module appears twice in the product's content.

**Fix**: Remove the duplicate - either from a module set or from direct `module()` calls.

### Investigation Tools

Use the Plugin Model Analyzer MCP for investigation:

```kotlin
// Find dependency path between modules
mcp__PluginModelAnalyzer__find_dependency_path(
  fromModule = "intellij.platform.vcs.impl",
  toModule = "intellij.c.core"
)

// Check which module sets contain a dependency
mcp__PluginModelAnalyzer__suggest_module_set_for_modules(
  moduleNames = ["intellij.c.core"]
)

// Check module reachability within a module set
mcp__PluginModelAnalyzer__check_module_reachability(
  moduleName = "intellij.platform.kernel",
  moduleSetName = "core.platform"
)

// Get module info including which products/sets include it
mcp__PluginModelAnalyzer__get_module_info(
  moduleName = "intellij.fullLine.cpp"
)
```

### Investigation Strategy

When you encounter a validation error:

1. **Identify the dependency chain**: Use `find_dependency_path` to understand why the dependency is needed

2. **Check if cross-plugin dependency**: Is the missing module in a non-bundled plugin? If so, check if the source module's loading allows cross-plugin deps.

3. **Find the right fix level**:
   - Missing module set in hierarchy → add the nested set
   - Product missing required set → add to product
   - Cross-plugin dep with non-critical loading → add to `additionalPlugins` in `ultimateGenerator.kt`
   - Truly missing infrastructure → add to appropriate module set

4. **Verify the fix**: Run "Generate Product Layouts" again

## See Also

- [Module Sets Documentation](module-sets.md) - How module sets work
- [Programmatic Content](programmatic-content.md) - Product content descriptors
- `DependencyValidation.kt` - Validation implementation
- `UltimateModuleSets.kt` - Non-bundled plugins configuration
