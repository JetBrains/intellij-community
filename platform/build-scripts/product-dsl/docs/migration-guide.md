# Migration Guide

This guide covers migrating products to the programmatic content system.

## Migration Path

To migrate a product to programmatic content:

1. **Implement `getProductContentDescriptor()`** in your `ProductProperties` class
   - Define module aliases with `alias()`
   - Add xi:includes with `deprecatedInclude()`
   - Include module sets with `moduleSet()`
   - Add individual modules with `module()` or `embeddedModule()`

2. **Extract extensions** to separate XML files (e.g., `*-customization.xml`)
   - Move `<extensions>` blocks from plugin.xml to dedicated files
   - Reference them via `deprecatedInclude()`

3. **Add pluginXmlPath** to `build/dev-build.json` for your product

4. **Run the generator** to create the complete plugin.xml:
   ```bash
   UltimateModuleSets.main()  # or CommunityModuleSets.main()
   ```

5. **Verify generated file** matches expected structure

6. **Commit all changes** to VCS (Kotlin code, generated XML, extracted extensions)

7. **Test compilation** to ensure product builds correctly

## Migrating from productImplementationModules

### Understanding the Difference

**`productImplementationModules` (deprecated):**
- Lists implementation modules (no XML descriptors) to bundle into product JARs
- Just loads classes into classloader
- Modules are NOT content modules (no plugin descriptors processed)
- Being phased out in favor of programmatic content descriptors

**`getProductContentDescriptor()` (modern):**
- Declares content modules via module sets and `module()`/`embeddedModule()`
- Content modules = have XML descriptors with extensions/services
- Implementation dependencies come via `includeDependencies = true`

### Migration Steps

**1. Identify content vs implementation modules**

Content modules (have .xml descriptors):
```bash
# Check if module has a descriptor
find_files_by_glob("**/moduleName.xml")

# Or look in resources directory
ls community/modulePath/resources/*.xml
```

Implementation modules (no descriptors):
- Just provide classes/resources
- Examples: `fleet.util.multiplatform`, `intellij.platform.webide.impl`

**2. Move content modules to programmatic descriptor**

If a module in `productImplementationModules` has a descriptor, it's incorrectly placed:

```kotlin
// ❌ WRONG - content module in productImplementationModules
productLayout.productImplementationModules = listOf(
  "fleet.andel"  // Has fleet.andel.xml descriptor!
)

// ✅ CORRECT - content module in programmatic descriptor
override fun getProductContentDescriptor() = productModules {
  module("fleet.andel")
  // Or better: use module set that already includes it
  moduleSet(CommunityModuleSets.essential())
}
```

**3. Keep implementation-only modules in productImplementationModules**

Implementation modules without descriptors can stay:

```kotlin
// ✅ OK - implementation modules without descriptors
productLayout.productImplementationModules = listOf(
  "intellij.platform.webide.impl",  // No descriptor
  "fleet.backend",  // No descriptor
  "fleet.util.network"  // No descriptor
)
```

**4. Use includeDependencies for transitive implementation deps**

Instead of listing all implementation dependencies explicitly:

```kotlin
// ❌ OLD - manually list all transitive implementation modules
productLayout.productImplementationModules = listOf(
  "fleet.andel",  // Content module (has descriptor)
  "fleet.util.multiplatform",  // Implementation dep of fleet.andel
  "fleet.backend"
)

// ✅ NEW - let includeDependencies handle transitive implementation modules
override fun getProductContentDescriptor() = productModules {
  embeddedModule("fleet.andel", includeDependencies = true)
  // This automatically includes fleet.util.multiplatform and other implementation deps
}

productLayout.productImplementationModules = listOf(
  "fleet.backend"  // Only product-specific implementation module
)
```

### Common Pitfalls

**Pitfall 1: Mixing content modules in productImplementationModules**

```kotlin
// ❌ BAD - fleet.rpc has descriptor, causes duplicates
productLayout.productImplementationModules = listOf(
  "fleet.rpc"  // Also comes from essential() → fleetMinimal()
)

override fun getProductContentDescriptor() = productModules {
  moduleSet(CommunityModuleSets.essential())  // Includes fleet.rpc
}
// Result: Duplicate content module declaration!
```

**Fix:** Remove content modules from `productImplementationModules`.

**Pitfall 2: Not checking transitive dependencies**

```kotlin
// ❌ BAD - assuming no duplicates without checking
productLayout.productImplementationModules = listOf(
  "fleet.util.multiplatform"  // Might come via includeDependencies!
)

override fun getProductContentDescriptor() = productModules {
  embeddedModule("fleet.andel", includeDependencies = true)
  // fleet.andel → fleet.util.core → fleet.util.multiplatform
}
```

**Fix:** Use Plugin Model Analyzer MCP to check transitive deps:

```kotlin
// Check ALL transitive dependencies
get_module_dependencies(
  moduleName = "fleet.andel",
  includeTransitive = true
)
```

**Pitfall 3: Forgetting includeDependencies only gets implementation modules**

```kotlin
// ❓ QUESTION - will this include fleet.util.core?
embeddedModule("fleet.andel", includeDependencies = true)

// ✅ ANSWER - NO! 
// fleet.util.core has a descriptor, so it's filtered out
// Only implementation modules (no descriptors) are included
```

### Verification Checklist

Before committing changes:

1. **Run Generate Product Layouts**
   ```bash
   # Via JetBrains MCP
   execute_run_configuration(name="Generate Product Layouts")
   
   # Or directly
   bazel run //platform/buildScripts:plugin-model-tool
   ```

2. **Check for duplicate content modules**
   - The generator will error if content modules are declared twice
   - Look for: "Plugin 'X' has duplicated content modules declarations"

3. **Verify tests pass**
   ```bash
   bazel test //platform/build-scripts/tests/testSrc/org/jetbrains/intellij/build:UltimatePluginModelTest
   ```

4. **Use MCP to analyze transitive dependencies**
   ```kotlin
   // Check what includeDependencies will include
   get_module_dependencies(
     moduleName = "your.module",
     includeTransitive = true
   )
   ```

## Migrating from PLATFORM_CORE_MODULES

The `PLATFORM_CORE_MODULES` constant in `PlatformModules.kt` is **deprecated**.

**Why it's deprecated:**
- Hard-coded list, not composable or reusable
- No clear hierarchy or structure
- String-based, error-prone
- Cannot be customized per product

**Migration:**

```kotlin
// OLD (deprecated)
for (module in PLATFORM_CORE_MODULES) {
  embeddedModule(module)
}

// NEW (recommended)
moduleSet(CommunityModuleSets.essentialMinimal())
// or for minimal products:
moduleSet(CommunityModuleSets.corePlatform())
```

### Choosing the Right Module Set

```
┌─────────────────────────────────────────────────┐
│ What type of product are you building?          │
└───────────────────┬─────────────────────────────┘
                    │
        ┌───────────┴───────────────────┬─────────────────────┐
        │                               │                     │
   Minimal tool                  Lightweight IDE         Full-featured IDE
   (analysis/inspection)         (basic editing)         (all features)
        │                               │                     │
        ▼                               ▼                     ▼
   corePlatform                   essentialMinimal        ide.common
                                  + specific sets         or ide.ultimate
```

| Module Set | Use Case |
|------------|----------|
| `corePlatform()` | Minimal tools without editing (CodeServer) |
| `essentialMinimal()` | Lightweight IDEs with basic editing |
| `essential()` | Full IDEs with language support |
| `ide.common` | IDEs with VCS, XML, common features |
| `ide.ultimate` | Full Ultimate IDEs |

---

## Example: Migrating CodeServer

**Current approach** (not recommended):
```kotlin
override fun getProductContentDescriptor(): ProductModulesContentSpec = productModules {
  alias("com.intellij.codeServer")
  
  // Only XML includes - modules not available at runtime
  deprecatedInclude("intellij.platform.analysis", "META-INF/Analysis.xml")
  deprecatedInclude("intellij.platform.core", "META-INF/Core.xml")
  deprecatedInclude("intellij.platform.projectModel", "META-INF/ProjectModel.xml")
  // ... 11 more deprecatedInclude calls
  
  // Only 5 modules total
  module("intellij.grid")
  module("intellij.libraries.jettison")
}
```

**Recommended approach** (for analysis tools without editing):
```kotlin
override fun getProductContentDescriptor(): ProductModulesContentSpec = productModules {
  alias("com.intellij.codeServer")
  
  // Use corePlatform for analysis tools (core platform without language editing)
  moduleSet(CommunityModuleSets.corePlatform())
  
  // Keep deprecatedInclude only for modules NOT in corePlatform
  deprecatedInclude("intellij.platform.indexing", "META-INF/Indexing.xml")
  deprecatedInclude("intellij.platform.codeStyle.impl", "META-INF/CodeStyle.xml")
  deprecatedInclude("intellij.platform.refactoring", "META-INF/RefactoringExtensionPoints.xml")
  deprecatedInclude("intellij.codeServer.core", "META-INF/codeserver-customization.xml")
  
  // Product-specific modules
  module("intellij.grid")
  module("intellij.grid.types")
  module("intellij.grid.csv.core.impl")
  module("intellij.grid.core.impl")
  module("intellij.libraries.jettison")
}
```

**Why corePlatform (not essentialMinimal)?**
CodeServer is an analysis/inspection tool that doesn't provide language editing capabilities:
- ✅ Needs: Core platform, analysis APIs, IDE extension points
- ❌ Doesn't need: Language support (lang.*), IDE editing (ide.impl), editor UI, search
- **corePlatform provides exactly what's needed** without unnecessary dependencies

**Benefits of using module sets:**
- Modules are actually available at runtime (not just XML extension points)
- Clear separation: analysis tools use corePlatform, editing IDEs use essentialMinimal
- Easier to maintain (fewer deprecatedInclude calls)
- Automatic updates when core platform evolves
