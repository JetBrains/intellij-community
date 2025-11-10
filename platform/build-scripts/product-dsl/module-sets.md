# Module Sets

Module sets are collections of modules that can be referenced as a single entity in product configurations.

## Overview

Module sets are now **defined in Kotlin code** in the following locations:
- **Community module sets**: `community/platform/build-scripts/product-dsl/src/CommunityModuleSets.kt`
- **Ultimate module sets**: `platform/buildScripts/src/productLayout/UltimateModuleSets.kt`

Each module set is defined as a Kotlin function returning a `ModuleSet` object. The XML files (following the pattern `intellij.moduleSets.<category>.<subcategory>.xml`) are **auto-generated** from this Kotlin code.

### Generating XML Files

To regenerate XML files from Kotlin code:

**Using JetBrains MCP (Recommended):**
```kotlin
mcp__jetbrains__execute_run_configuration(configurationName="Generate Product Layouts")
```

**Using Gradle:**
```bash
# For all products (community + ultimate)
./gradlew :platform.buildScripts:runIde -PmainClass=com.intellij.platform.commercial.buildScripts.productLayout.UltimateModuleSets

# For community products only
./gradlew :intellij.platform.buildScripts:runIde -PmainClass=org.jetbrains.intellij.build.productLayout.CommunityModuleSets
```

**From IDE:**
Run the "Generate Product Layouts" run configuration.

### Querying Module Sets

To query module set structure, relationships, and usage programmatically, use the **JSON analysis endpoint**:
```bash
UltimateModuleSets.main(args = ["--json"])
```

See the [Programmatic Content](programmatic-content.md#json-analysis-endpoint) documentation for details.

## Creating a New Module Set

See `/create-module-set` slash command for detailed instructions on creating a new module set.

## Available Module Sets

Module sets are defined in Kotlin code:

- **Community module sets**: See `community/platform/build-scripts/product-dsl/src/CommunityModuleSets.kt`
- **Ultimate module sets**: See `platform/buildScripts/src/productLayout/UltimateModuleSets.kt`

Each module set is defined as a function (e.g., `fun essential(): ModuleSet`) within these classes.

### Querying Module Sets

To discover available module sets and their composition:

1. **Browse the source code** - Open the Kotlin files listed above to see all available module sets
2. **Use the JSON endpoint** - Run `UltimateModuleSets.main(args = ["--json"])` to get comprehensive analysis
3. **Check generated XML** - The generated XML files (e.g., `intellij.moduleSets.essential.xml`) contain expanded module lists

For detailed module composition, includes/includedBy relationships, and product usage, use the [JSON analysis endpoint](#querying-module-sets).