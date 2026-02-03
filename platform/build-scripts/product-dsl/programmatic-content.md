# Programmatic Content Modules

This document describes the programmatic content module system that allows products to define their module content in Kotlin code instead of static XML files.

## Overview

The programmatic content system provides two complementary mechanisms:

1. **Runtime injection** (build time): Content is injected via `layout.withPatch` during the build
2. **Static generation** (VCS): Content is generated into plugin.xml files and committed to version control

Both mechanisms use the same Kotlin DSL and ensure the content stays synchronized.

## Architecture

### Build-Time Injection (PlatformModules.kt)

At build time, `processProgrammaticModules()` injects content modules into the product's plugin.xml:

- Reads `ProductProperties.getProductContentModules()`
- Looks for `<!-- programmatic-content-start -->` and `<!-- programmatic-content-end -->` marker tags
- Removes existing content between markers
- Generates fresh `<content>` blocks for each module set
- Injects the content between the markers

**Key property**: Build **always** replaces content between markers, ensuring dev mode works without running the static generator.

### Static Generation (ModuleSetBuilder.kt)

Static generation creates XML files for non-dev mode:

- `buildProductContentXml()`: Generates XML content from `ProductModulesContentSpec`
- `generateProductXml()`: Replaces content between markers in plugin.xml files
- `generateGatewayProductXml()`: Helper for Gateway product (example implementation)

Static generation is triggered by running:

Run the `Generate Product Layouts` run configuration, or directly invoke the appropriate main method:

```bash
CommunityModuleSets.main()  # for community products
UltimateModuleSets.main()   # for ultimate + community + products
```

Note: The generated XML comments will automatically indicate which command to run based on the product's module content.

## Usage

### 1. Define Product Content in Kotlin

In your `ProductProperties` class:

```kotlin
override fun getProductContentModules(): ProductModulesContentSpec {
  return productModules {
    // XML includes (optional - can also be defined in plugin.xml manually)
    // Specify module name and resource path within that module
    deprecatedInclude("intellij.platform.resources", "META-INF/PlatformLangPlugin.xml")
    deprecatedInclude("intellij.gateway", "META-INF/Gateway.xml")
    
    // Ultimate-only includes (only included in Ultimate builds)
    // When inlining: Skipped in Community builds
    // When NOT inlining: Generates xi:include with xi:fallback for graceful handling
    deprecatedInclude("intellij.platform.extended.community.impl", "META-INF/community-extensions.xml", ultimateOnly = true)

    // Include module sets
    moduleSet(CommunityModuleSets.essential())
    moduleSet(CommunityModuleSets.vcs())
    moduleSet(UltimateModuleSets.ssh())

    // Add individual modules
    module("intellij.platform.collaborationTools")
    embeddedModule("intellij.gateway.ssh")

    // Exclude specific modules
    exclude("intellij.unwanted.module")

    // Override loading mode
    override("some.module", ModuleLoadingRule.OPTIONAL)
  }
}
```

### 2. Add pluginXmlPath to dev-build.json

Register the product's plugin.xml file path in `build/dev-build.json`:

```json
"DataSpell": {
  "modules": [...],
  "class": "com.intellij.dataspell.build.DataSpellProperties",
  "pluginXmlPath": "dataspell/ide/resources/META-INF/DataSpellPlugin.xml"
}
```

This tells the generator which file to regenerate for this product.

### 3. Generate Plugin.xml

Run the generator to create the complete plugin.xml file:

```bash
UltimateModuleSets.main()   # for ultimate + community + products
CommunityModuleSets.main()  # for community products only
```

Or use the IDE's "Generate Product Layouts" run configuration.

This will generate a complete plugin.xml file like:

```xml
  <!-- DO NOT EDIT: This file is auto-generated from Kotlin code -->
  <!-- To regenerate, run 'Generate Product Layouts' or directly UltimateModuleSets.main() -->
  <!-- Source: com.intellij.dataspell.build.DataSpellProperties -->
<idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
  <module value="com.intellij.modules.dataspell"/>
  <module value="com.intellij.modules.python-core-capable"/>
  <module value="com.intellij.platform.ide.provisioner"/>

  <xi:include href="/META-INF/pycharm-core.xml"/>
  <xi:include href="/META-INF/ultimate.xml"/>
  <xi:include href="/META-INF/dataspell-customization.xml"/>
  <xi:include href="/META-INF/intellij.moduleSets.commercial.xml"/>
  <xi:include href="/META-INF/intellij.moduleSets.ide.common.xml"/>
  <!-- ... -->
  
  <content namespace="jetbrains">
    <!-- <editor-fold desc="additional"> -->
    <module name="intellij.python.scientific"/>
    <module name="intellij.platform.ide.newUiOnboarding"/>
    <!-- ... -->
    <!-- </editor-fold> -->
  </content>
</idea-plugin>
```

## Best Practices

### Module Sets vs. deprecatedInclude() vs. direct modules

Choose the appropriate mechanism based on your needs:

**Use `moduleSet()` when:**
- You need a cohesive group of modules (e.g., `essentialMinimal`, `vcs`, `ssh`)
- You want to reuse a common set across multiple products
- You need the full module functionality, not just XML extension points
- The module set provides platform infrastructure (e.g., `corePlatform` for core platform modules)

**Use `deprecatedInclude()` when:**
- You only need XML extension points/components from a specific module
- The module is not part of any module set yet
- You need to reference product-specific customization XML files
- The XML file doesn't define content modules (just extensions/listeners)

**Use `module()` or `embeddedModule()` when:**
- You need a single content module not in any module set
- The module is specific to your product
- You want explicit control over loading mode

**Key principle**: Prefer module sets for core platform functionality, use `deprecatedInclude()` only for XML-only includes.

### Migrating from PLATFORM_CORE_MODULES

The `PLATFORM_CORE_MODULES` constant in `PlatformModules.kt` is **deprecated**. It was a hard-coded list of modules that are included in lib/app.jar in all IntelliJ-based IDEs.

**Why it's deprecated:**
- Hard-coded list, not composable or reusable
- No clear hierarchy or structure
- String-based, error-prone
- Cannot be customized per product

**Migration path:**

Replace `PLATFORM_CORE_MODULES` references with module sets:

```kotlin
// OLD (deprecated)
for (module in PLATFORM_CORE_MODULES) {
  embeddedModule(module)
}

// NEW (recommended)
moduleSet(CommunityModuleSets.essentialMinimal())
// or for minimal products:
moduleSet(CommunityModuleSets.corePlatform())  // when public
```

**What module set to use:**
- **`essentialMinimal()`**: For lightweight IDE products (includes coreLang, backend/frontend split, editor, search)
- **`essential()`**: For full IDE products with language support
- **`ide.common`**: For IDE products with VCS, XML, and common IDE features
- **`corePlatform()`** (when public): For minimal products that need core platform without lang/ide.impl

### Choosing the Right Module Set for Your Product

Use this decision tree to select the appropriate module set:

```
┌─────────────────────────────────────────────────┐
│ What type of product are you building?         │
└───────────────────┬─────────────────────────────┘
                    │
        ┌───────────┴───────────────────┬─────────────────────┐
        │                               │                     │
   Minimal tool                  Lightweight IDE         Full-featured IDE
   (analysis/inspection)         (basic editing)         (all features)
        │                               │                     │
        ▼                               ▼                     ▼
   corePlatform                   essentialMinimal        ide.common
   (when public)                  + specific sets         or ide.ultimate
```

**Example products:**
- **CodeServer** (analysis/inspection tool without editing): Uses `corePlatform`
- **WebStorm** (full IDE): Uses `ide.common` + web-specific modules
- **IntelliJ IDEA Ultimate**: Uses `ide.ultimate` + all language plugins

### Example: Migrating CodeServer

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

## How It Works

### File Generation Strategy

The system generates complete plugin.xml files from Kotlin code:

1. **Static generation**: The entire plugin.xml is generated from `getProductContentDescriptor()` 
2. **Auto-generated header**: Each file includes a "DO NOT EDIT" comment indicating it's generated
3. **VCS-committed**: Generated files are committed to version control
4. **Build-time injection**: At runtime, `buildProductContentXml()` is also called during build for validation

### Generated Content Structure

Each module set generates a separate `<content>` block with a `source` attribute for traceability:

```xml
<content namespace="jetbrains" source="essential">
  <module name="..." loading="embedded"/>
  <module name="..."/>
</content>
<content namespace="jetbrains" source="vcs">
  <module name="..."/>
</content>
<content namespace="jetbrains" source="additional">
  <module name="..." loading="optional"/>
</content>
```

### Module Processing Rules

1. **Nested sets**: Modules from nested module sets are filtered out to avoid duplicates
2. **Exclusions**: Modules in `excludedModules` are skipped
3. **Loading overrides**: `moduleLoadingOverrides` map takes precedence over module's default loading mode
4. **Empty blocks**: Content blocks with no modules (after filtering) are omitted

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

## Example: Gateway

Gateway (`GatewayProperties.kt`) uses programmatic content:

```kotlin
override fun getProductContentModules(): ProductModulesContentSpec {
  return productModules {
    moduleSet(CommunityModuleSets.essential())
    moduleSet(CommunityModuleSets.vcs())
    moduleSet(UltimateModuleSets.ssh())
    embeddedModule("intellij.gateway.ssh")

    module("intellij.platform.collaborationTools")
    module("intellij.platform.collaborationTools.auth")
    // ...
  }
}
```

The content is generated into `/remote-dev/gateway/resources/META-INF/plugin.xml`.

## Ultimate-Only Includes

The `ultimateOnly` flag on `deprecatedInclude()` enables conditional inclusion of resources that only exist in Ultimate builds.

### Behavior

**When inlining** (`inlineXmlIncludes = true`):
- Community builds: Skip the include entirely
- Ultimate builds: Inline the content normally

**When NOT inlining** (`inlineXmlIncludes = false`):
- Generates `<xi:include>` with `<xi:fallback/>` wrapper for graceful handling:
  ```xml
  <xi:include href="/META-INF/community-extensions.xml">
    <xi:fallback/>
  </xi:include>
  ```
- Community builds: XInclude processor skips gracefully (file not found, fallback used)
- Ultimate builds: XInclude processor includes the file normally

### Example

```kotlin
override fun getProductContentModules(): ProductModulesContentSpec {
  return productModules {
    // Regular include - always processed
    deprecatedInclude("intellij.pycharm.community", "META-INF/pycharm-core.xml")
    
    // Ultimate-only - conditionally processed
    deprecatedInclude("intellij.platform.extended.community.impl", 
                     "META-INF/community-extensions.xml", 
                     ultimateOnly = true)
  }
}
```

**Generated XML (Community build)**:
```xml
<xi:include href="/META-INF/pycharm-core.xml"/>
<xi:include href="/META-INF/community-extensions.xml">
  <xi:fallback/>
</xi:include>
```

**Generated XML (Ultimate build)**:
```xml
<xi:include href="/META-INF/pycharm-core.xml"/>
<xi:include href="/META-INF/community-extensions.xml"/>
```

### Use Cases

Use `ultimateOnly = true` when:
1. The included XML file exists only in Ultimate repository
2. Multiple products (both Community and Ultimate variants) share the same descriptor
3. You need backward compatibility during migration (xi:fallback allows runtime resolution)

## Implementation Details

### Key Functions

- **`buildProductContentXml()`** (generator.kt): Generates complete XML from ProductModulesContentSpec
- **`generateProductXml()`** (generator.kt): Writes generated XML to plugin.xml file
- **`generateAllProductXmlFiles()`** (generator.kt): Batch generation for all registered products
- **`collectAndValidateAliases()`** (generator.kt): Validates module aliases for duplicates

## JSON Analysis Endpoint

The module set system provides a JSON analysis endpoint for programmatic querying and tooling integration. This endpoint is used by the Plugin Model Analyzer MCP server and other build tools.

### Usage

Run the module set main function with the `--json` flag:

```bash
# Generate complete analysis for all products and module sets
UltimateModuleSets.main(args = ["--json"])

# Community products only
CommunityModuleSets.main(args = ["--json"])
```

### Filtering Output

Use the `--json` flag with a filter to get specific sections:

```bash
# Get only products
--json='{"filter":"products"}'

# Get only module sets
--json='{"filter":"moduleSets"}'

# Include duplicate analysis
--json='{"includeDuplicates":true}'
```

### Output Structure

The JSON output contains comprehensive analysis of the module system:

#### 1. Module Distribution

Maps each module to the module sets and products that include it:

```json
{
  "moduleDistribution": {
    "intellij.platform.vcs.impl": {
      "inModuleSets": ["vcs", "ide.common"],
      "inProducts": ["WebStorm", "GoLand", "CLion", "PyCharm", ...]
    }
  }
}
```

**Use case:** Find where a specific module is used across the codebase.

#### 2. Module Set Hierarchy

Shows the include relationships between module sets:

```json
{
  "moduleSetHierarchy": {
    "ide.common": {
      "includes": ["essential", "vcs"],
      "includedBy": ["ide.ultimate"],
      "moduleCount": 145
    }
  }
}
```

**Use case:** Understand module set dependencies and nesting structure.

#### 3. Module Usage Index

Comprehensive reverse lookup with source file paths:

```json
{
  "moduleUsageIndex": {
    "modules": {
      "intellij.platform.vcs.impl": {
        "moduleSets": [
          {
            "name": "vcs",
            "location": "community",
            "sourceFile": "community/platform/build-scripts/product-dsl/src/CommunityModuleSets.kt"
          }
        ],
        "products": [
          {
            "name": "WebStorm",
            "sourceFile": "platform/buildScripts/src/productLayout/UltimateModuleSets.kt"
          }
        ]
      }
    }
  }
}
```

**Use case:** Trace module ownership and find where to make changes.

#### 4. Product Composition Analysis

Detailed breakdown of each product's composition:

```json
{
  "productCompositionAnalysis": {
    "CLion": {
      "composition": {
        "totalAliases": 3,
        "totalModuleSets": 12,
        "totalDirectModules": 45,
        "totalModules": 523
      },
      "operations": [
        {"type": "alias", "value": "com.jetbrains.modules.cidr.lang"},
        {"type": "moduleSet", "value": "commercial"},
        {"type": "module", "value": "intellij.clion.core"}
      ]
    }
  }
}
```

**Use case:** Analyze product composition and optimize module dependencies.

#### 5. Duplicate Analysis (Optional)

When `includeDuplicates: true` is set, detects duplicate xi:include elements:

```json
{
  "duplicateAnalysis": {
    "ReSharper Backend": {
      "/META-INF/intellij.moduleSets.essential.xml": [
        {
          "directInclude": true,
          "deprecatedIncludeRefs": [
            "intellij.platform.resources -> /META-INF/PlatformLangPlugin.xml"
          ]
        }
      ]
    }
  }
}
```

**Use case:** Identify redundant includes that can be removed.

### Integration with MCP Server

The Plugin Model Analyzer MCP server (`build/mcp-servers/module-analyzer`) uses this JSON endpoint to provide:

- `analyze_module_structure` - Complete module system analysis
- `get_module_info` - Query specific module details
- `find_module_paths` - Trace module to product paths
- `get_module_set_hierarchy` - Query module set relationships
- `list_products` - List products filtered by criteria
- `validate_community_products` - Ensure community/ultimate separation

### Implementation

The JSON generation is implemented in:
- `ModuleSetRunner.kt` - Orchestration and CLI parsing
- `ModuleSetJsonExport.kt` - JSON generation logic
- `ModuleSetDiscovery.kt` - Module set discovery via reflection

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

## Benefits

1. **Type safety**: Kotlin code with IDE support (autocomplete, refactoring)
2. **Reusability**: Share module sets across products
3. **Single source of truth**: One Kotlin definition for both dev and non-dev modes
4. **Maintainability**: Easier to see what modules a product includes
5. **VCS-friendly**: Static files work without dev mode infrastructure
6. **Programmatic access**: JSON endpoint enables tooling and automation

## See Also

- [Module Sets Documentation](module-sets.md) - How module sets work and composition
- [Validation Documentation](validation.md) - Dependency validation and troubleshooting
- `ProductModulesContentSpec` class documentation
- `ModuleSet` and `ContentModule` classes
- Example: `GatewayProperties.getProductContentModules()`