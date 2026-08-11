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
    
    // Include module sets
    moduleSet(CommunityModuleSets.essential())
    moduleSet(CommunityModuleSets.vcs())
    moduleSet(UltimateModuleSets.webIde())

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
"GoLand": {
  "modules": [...],
  "class": "org.jetbrains.intellij.build.goland.GoLandProperties",
  "pluginXmlPath": "goland/resources/META-INF/GoLandPlugin.xml"
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
  <!-- Source: org.jetbrains.intellij.build.goland.GoLandProperties -->
<idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
  <module value="com.intellij.modules.goland"/>
  <module value="com.intellij.platform.ide.provisioner"/>

  <xi:include href="/META-INF/ultimate.xml"/>
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

### Choosing the Right Module Set

See [migration-guide.md](migration-guide.md) for guidance on choosing module sets and migrating from legacy platform core module declarations.

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

## Migration

See [migration-guide.md](migration-guide.md) for migration guides including:
- General migration path
- Migrating from productImplementationModules
- Example: Migrating CodeServer

## Example: Gateway

Gateway (`GatewayProperties.kt`) uses programmatic content:

```kotlin
override fun getProductContentModules(): ProductModulesContentSpec {
  return productModules {
    moduleSet(CommunityModuleSets.essential())
    moduleSet(CommunityModuleSets.vcs())
    moduleSet(UltimateModuleSets.webIde())
    embeddedModule("intellij.gateway.ssh")

    module("intellij.platform.collaborationTools")
    module("intellij.platform.collaborationTools.auth")
    // ...
  }
}
```

The content is generated into `/remote-dev/gateway/resources/META-INF/plugin.xml`.

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

## Implementation Details

### Key Functions

- **`buildProductContentXml()`** (generator.kt): Generates complete XML from ProductModulesContentSpec
- **`generateProductXml()`** (generator.kt): Writes generated XML to plugin.xml file
- **`generateAllProductXmlFiles()`** (generator.kt): Batch generation for all registered products
- **`collectAndValidateAliases()`** (generator.kt): Validates module aliases for duplicates

## JSON Analysis Endpoint

The module set system provides a JSON analysis endpoint for programmatic querying and tooling integration. The JSON export is generated from the in-memory PluginGraph built from product DSL and module sets; it does not parse generated plugin.xml files or descriptors from disk.

### Usage

Run the analyzer through Bazel from the repository root:

```bash
bazel run --ui_event_filters=-info --noshow_progress //platform/buildScripts:plugin-model-tool -- --json='<request-json>'
```

Transport options:

```bash
--json='{"filter":"summary","limit":5}'
--json=-
--json=@/private/tmp/plugin-model-query.json
```

Plain `--json` emits the complete model and is usually too large for routine investigation. Prefer compact filters.

### Common Filters

```json
{"filter":"summary","limit":5}
{"filter":"moduleInfo","module":"intellij.platform.vcs.impl","limit":20}
{"filter":"moduleDependencies","module":"fleet.andel","includeTransitive":true}
{"filter":"dependencyPath","fromModule":"intellij.platform.vcs.impl","toModule":"intellij.platform.projectModel.impl","includeScopes":true}
{"filter":"moduleSetQuery","moduleSet":"ide.common","limit":50}
{"filter":"productQuery","usesModuleSet":"ide.common","limit":30}
{"filter":"validation","check":"embedded_dependency_closure","pluginSourceOnly":true}
```

See the `plugin-model-analyzer` skill for the full agent-facing request catalog.

### Output Structure

The JSON output always contains a `timestamp` and one result property named after the requested filter. Compact results include counts and truncated item lists by default; pass a higher `limit` or `details:true` when expanded arrays are needed.

Legacy full-model filters are still available for bulk analysis: `products`, `moduleSets`, `composition`, `duplicates`, `product`, `moduleSet`, `modulePaths`, `moduleDependencies`, `moduleOwners`, `moduleReachability`, `dependencyPath`, `productUsage`, `embeddedDependencyClosure`, and `mergeImpact`.

### Implementation

The JSON generation is implemented in:
- `ModuleSetRunner.kt` - builds PluginGraph, handles CLI, dispatches JSON export
- `ModuleSetJsonExport.kt` - JSON generation from PluginGraph
- `ModuleSetDiscovery.kt` - Module set discovery via reflection



## Benefits

1. **Type safety**: Kotlin code with IDE support (autocomplete, refactoring)
2. **Reusability**: Share module sets across products
3. **Single source of truth**: One Kotlin definition for both dev and non-dev modes
4. **Maintainability**: Easier to see what modules a product includes
5. **VCS-friendly**: Static files work without dev mode infrastructure
6. **Programmatic access**: JSON endpoint enables tooling and automation

## See Also

- [Module Sets Documentation](module-sets.md) - How module sets work and composition
- [Validation Rules](validation-rules.md) - Dependency validation rules
- [Error Reference](errors.md) - Troubleshooting validation errors