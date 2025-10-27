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
    include("intellij.platform.resources", "META-INF/PlatformLangPlugin.xml")
    include("intellij.gateway", "META-INF/Gateway.xml")

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

### 2. Add Marker Tags to plugin.xml

Add the marker tags where you want the content injected:

```xml
<idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude">
  <module value="com.jetbrains.gateway"/>

  <xi:include href="/META-INF/PlatformLangPlugin.xml"/>

  <!-- programmatic-content-start -->
  <!-- Programmatic content modules will be generated here -->
  <!-- Run UltimateModuleSets.main() to regenerate -->
  <!-- programmatic-content-end -->
</idea-plugin>
```

### 3. Generate Static Content

Run the generator to populate the content between markers:

```bash
./gradlew :platform.buildScripts:run
```

This will generate content like:

```xml
<!-- programmatic-content-start -->
  <!-- DO NOT EDIT: This content is auto-generated from Kotlin code -->
  <!-- To regenerate, run 'Generate Product Layouts' or directly: UltimateModuleSets.main() -->
  <!-- Source: see getProductContentModules() in GatewayProperties.kt -->

  <xi:include href="/META-INF/PlatformLangPlugin.xml"/>
  <xi:include href="/META-INF/Gateway.xml"/>

  <content namespace="jetbrains" source="essential">
    <module name="intellij.platform.settings.local"/>
    <module name="intellij.platform.backend"/>
    <!-- ... -->
  </content>
  <content namespace="jetbrains" source="vcs">
    <module name="intellij.platform.vcs"/>
    <!-- ... -->
  </content>
  <!-- programmatic-content-end -->
```

## How It Works

### Deduplication Strategy

The marker-based approach prevents duplicate content:

1. **Dev mode**: Build reads markers, removes old content, injects fresh content from Kotlin code
2. **Non-dev mode**: Static XML already contains the content (generated once, committed to VCS)
3. **No conflicts**: Build always replaces whatever is between markers

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

1. **Implement `getProductContentModules()`** in your `ProductProperties` class
2. **Add marker tags** to your product's plugin.xml
3. **Run the generator** to populate initial content
4. **Commit the generated content** to VCS
5. **Verify** both dev mode (build injects fresh) and non-dev mode (uses static) work

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

## Implementation Details

### Key Functions

- **`processProgrammaticModules()`** (PlatformModules.kt:610): Build-time injection
- **`buildProductContentXml()`** (ModuleSetBuilder.kt:231): Static XML generation
- **`generateProductXml()`** (ModuleSetBuilder.kt:299): Marker replacement
- **`generateGatewayProductXml()`** (ModuleSetBuilder.kt:346): Gateway-specific helper

### Marker Tags

- `<!-- programmatic-content-start -->`: Start marker (self-closing tag)
- `<!-- programmatic-content-end -->`: End marker (self-closing tag)

These tags are preserved in the XML and used by both static generation and build-time injection.

### Backward Compatibility

If marker tags are not found:
- **Build time**: Content is appended at the end (existing behavior)
- **Static generation**: File is skipped (no changes)

This ensures products can migrate incrementally without breaking existing builds.

## Benefits

1. **Type safety**: Kotlin code with IDE support (autocomplete, refactoring)
2. **Reusability**: Share module sets across products
3. **Single source of truth**: One Kotlin definition for both dev and non-dev modes
4. **Maintainability**: Easier to see what modules a product includes
5. **VCS-friendly**: Static files work without dev mode infrastructure

## See Also

- [Module Sets Documentation](module-sets.md)
- `ProductModulesContentSpec` class documentation
- `ModuleSet` and `ContentModule` classes
- Example: `GatewayProperties.getProductContentModules()`