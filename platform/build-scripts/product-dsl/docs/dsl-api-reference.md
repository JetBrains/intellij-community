# DSL API Reference

Complete reference for the product-dsl Kotlin DSL functions used to define product module composition.

## Overview

The product-dsl provides two main DSL entry points:

| DSL | Purpose | Source File |
|-----|---------|-------------|
| `productModules {}` | Define product content specification | `ProductModulesContentSpec.kt` |
| `moduleSet() {}` | Define reusable module collections | `ModuleSetBuilder.kt` |

## Product Content DSL

### `productModules {}` - Create Product Specification

Entry point for defining a product's module composition.

```kotlin
fun productModules(block: ProductModulesContentSpecBuilder.() -> Unit): ProductModulesContentSpec
```

**Example:**
```kotlin
override fun getProductContentDescriptor(): ProductModulesContentSpec = productModules {
  alias("com.intellij.modules.idea")
  vendor("JetBrains")
  
  moduleSet(essential())
  moduleSet(vcs())
  
  embeddedModule("intellij.platform.additional")
  
  bundledPlugins(listOf("intellij.java.plugin"))
}
```

---

## Product Builder Functions

These functions are available inside the `productModules {}` block:

### `alias()` - Add Module Alias

```kotlin
fun alias(value: String)
```

Adds a product module alias for `<module value="..."/>` declaration. Plugins can depend on this alias.

**Example:**
```kotlin
alias("com.intellij.modules.idea")
alias("com.jetbrains.gateway")
```

---

### `vendor()` - Set Product Vendor

```kotlin
fun vendor(value: String)
```

Sets the product vendor for the `<vendor>` tag in plugin.xml.

**Example:**
```kotlin
vendor("JetBrains")
```

---

### `include()` - Include Another Spec

```kotlin
fun include(spec: ProductModulesContentSpec)
```

Merges another `ProductModulesContentSpec` into this builder. Enables composition of reusable spec fragments.

**Example:**
```kotlin
productModules {
  include(commonCapabilityAliases())  // Reusable aliases
  include(platformCommonIncludes())   // Reusable deprecatedIncludes
  moduleSet(commercialIdeBase())
}
```

---

### `deprecatedInclude()` - Add XML Include

```kotlin
fun deprecatedInclude(
  moduleName: String,
  resourcePath: String,
  ultimateOnly: Boolean = false,
  optional: Boolean = false
)
```

Adds an xi:include directive to include XML content from a module's resources.

| Parameter | Description |
|-----------|-------------|
| `moduleName` | Module containing the resource |
| `resourcePath` | Path within the module (e.g., `META-INF/Plugin.xml`) |
| `ultimateOnly` | If true, skipped in Community builds |
| `optional` | If true, always uses xi:fallback (never inlined) |

**Example:**
```kotlin
deprecatedInclude("intellij.platform.resources", "META-INF/PlatformLangPlugin.xml")
deprecatedInclude("intellij.ultimate.resources", "META-INF/UltimatePlugin.xml", ultimateOnly = true)
deprecatedInclude("intellij.rider.languages", "intellij.rider.languages.xml", optional = true)
```

---

### `moduleSet()` - Include Module Set

```kotlin
// Without overrides
fun moduleSet(set: ModuleSet)

// With loading overrides
fun moduleSet(set: ModuleSet, block: ModuleLoadingOverrideBuilder.() -> Unit)
```

Includes a module set. When overrides are provided, the set is inlined instead of using xi:include.

**Example:**
```kotlin
// Simple inclusion
moduleSet(essential())
moduleSet(vcs())

// With loading overrides (causes inlining)
moduleSet(commercialIdeBase()) {
  overrideAsEmbedded("intellij.rd.platform")
  overrideAsEmbedded("intellij.rd.ui")
}
```

---

### `module()` - Add Individual Module

```kotlin
fun module(
  name: String,
  loading: ModuleLoadingRuleValue = ModuleLoadingRuleValue.OPTIONAL,
  allowedMissingPluginIds: List<String> = emptyList()
)
```

Adds an individual module to `additionalModules`. Use for modules not in any module set.
`allowedMissingPluginIds` is only used for DSL test plugins to suppress unresolved plugin-owned auto-add deps
discovered from this module.

**Example:**
```kotlin
module("intellij.platform.specific.feature")
module("intellij.some.module", ModuleLoadingRuleValue.REQUIRED)
```

---

### `embeddedModule()` - Add Embedded Module

```kotlin
fun embeddedModule(name: String, allowedMissingPluginIds: List<String> = emptyList())
```

Adds a module with `loading="embedded"`. Use for modules that must load in the core classloader.
`allowedMissingPluginIds` is only used for DSL test plugins to suppress unresolved plugin-owned auto-add deps
discovered from this module.

**Example:**
```kotlin
embeddedModule("intellij.platform.core.extension")
```

---

### `requiredModule()` - Add Required Module

```kotlin
fun requiredModule(name: String, allowedMissingPluginIds: List<String> = emptyList())
```

Adds a module with `loading="required"`. Use for test framework modules.
`allowedMissingPluginIds` is only used for DSL test plugins to suppress unresolved plugin-owned auto-add deps
discovered from this module.

**Example:**
```kotlin
requiredModule("intellij.libraries.junit5")
```

---

### `bundledPlugins()` - Specify Bundled Plugins

```kotlin
fun bundledPlugins(pluginModules: List<String>)
```

Specifies bundled plugin modules for automatic dependency generation. These are JPS modules containing `META-INF/plugin.xml`.

**Example:**
```kotlin
bundledPlugins(listOf(
  "intellij.java.plugin",
  "intellij.kotlin.plugin",
  "intellij.git4idea"
))
```

---

### `allowMissingDependencies()` - Allow Missing Modules

```kotlin
fun allowMissingDependencies(vararg modules: String)
fun allowMissingDependencies(modules: List<String>)
```

Allows specific modules to be missing during validation. Use for modules provided by plugin layouts.

**Example:**
```kotlin
allowMissingDependencies("com.jetbrains.cidr.lang", "com.jetbrains.cidr.execution")
```

---

### `testPlugin {}` - Define Test Plugin

```kotlin
fun testPlugin(
  pluginId: String,
  name: String,
  pluginXmlPath: String,
  additionalBundledPluginTargetNames: List<String> = emptyList(),
  allowedMissingPluginIds: List<String> = emptyList(),
  block: ProductModulesContentSpecBuilder.() -> Unit
)
```

Defines a test plugin with programmatically generated plugin.xml.
Auto-add fills in unresolvable transitive test deps under the `additional` region, so keep the spec minimal.
`additionalBundledPluginTargetNames` lists extra bundled plugin target (JPS module) names used for resolution.
Use `allowedMissingPluginIds` (plugin IDs) to suppress errors for unresolvable plugin dependencies (it does not add the dependency).
For more precise suppression tied to a specific module, pass `allowedMissingPluginIds` to `module()`, `embeddedModule()`,
or `requiredModule()` inside the test plugin block.

**Example:**
```kotlin
testPlugin(
  pluginId = "intellij.python.junit5Tests.plugin",
  name = "Python Tests Plugin",
  pluginXmlPath = "python/junit5Tests/plugin/testResources/META-INF/plugin.xml"
) {
  moduleSet(CommunityModuleSets.platformTestFrameworksCore())
  module("intellij.tools.testsBootstrap")
  module("intellij.python.testFramework")
}
```

See [test-plugins.md](test-plugins.md) for details.

---

## Module Set DSL

### `moduleSet() {}` - Create Module Set

Entry point for defining a reusable module collection.

```kotlin
fun moduleSet(
  name: String,
  alias: String? = null,
  outputModule: String? = null,
  selfContained: Boolean = false,
  includeDependencies: Boolean = false,
  block: ModuleSetBuilder.() -> Unit
): ModuleSet
```

| Parameter | Description |
|-----------|-------------|
| `name` | Identifier for the set (e.g., `"vcs"`, `"essential.minimal"`) |
| `alias` | Optional module alias for `<module value="..."/>` |
| `outputModule` | Module whose resources dir receives generated XML |
| `selfContained` | If true, validates in isolation (all deps must be within set) |
| `includeDependencies` | Default for embedded modules to include transitive deps |

**Examples:**
```kotlin
// Simple module set
fun vcs() = moduleSet("vcs") {
  module("intellij.platform.vcs.impl")
  module("intellij.platform.vcs.log")
  embeddedModule("intellij.platform.vcs")
}

// With alias
fun xml() = moduleSet("xml", alias = "com.intellij.modules.xml") {
  embeddedModule("intellij.xml.dom")
  embeddedModule("intellij.xml.psi")
}

// With custom output location
fun corePlatform() = moduleSet("core.platform", outputModule = "intellij.platform.ide.core") {
  // ...
}

// With dependency auto-inclusion
fun essential() = moduleSet("essential", includeDependencies = true) {
  embeddedModule("intellij.platform.core")  // Inherits includeDependencies=true
}
```

---

## Module Set Builder Functions

These functions are available inside the `moduleSet() {}` block:

### `module()` - Add Regular Module

```kotlin
fun module(
  name: String,
  loading: ModuleLoadingRuleValue = ModuleLoadingRuleValue.OPTIONAL,
  allowedMissingPluginIds: List<String> = emptyList()
)
```

Adds a module with default or specified loading mode.
`allowedMissingPluginIds` is only used for DSL test plugins to suppress unresolved plugin-owned auto-add deps
discovered from this module.

---

### `embeddedModule()` - Add Embedded Module

```kotlin
fun embeddedModule(name: String, allowedMissingPluginIds: List<String> = emptyList())
```

Adds a module with `loading="embedded"`.
`allowedMissingPluginIds` is only used for DSL test plugins to suppress unresolved plugin-owned auto-add deps
discovered from this module.

---

### `requiredModule()` - Add Required Module

```kotlin
fun requiredModule(name: String, allowedMissingPluginIds: List<String> = emptyList())
```

Adds a module with `loading="required"`.
`allowedMissingPluginIds` is only used for DSL test plugins to suppress unresolved plugin-owned auto-add deps
discovered from this module.

---

### `moduleSet()` - Include Nested Module Set

```kotlin
fun moduleSet(set: ModuleSet)
```

Includes another module set, creating hierarchical composition.

**Example:**
```kotlin
fun ideCommon() = moduleSet("ide.common") {
  moduleSet(essential())
  moduleSet(vcs())
  moduleSet(xml())
}
```

---

## Loading Override Builder

Available when using `moduleSet(set) { ... }` with overrides:

### `overrideAsEmbedded()` - Override to Embedded

```kotlin
fun overrideAsEmbedded(moduleName: String)
```

Overrides a module's loading to `embedded`.

---

### `overrideAsRequired()` - Override to Required

```kotlin
fun overrideAsRequired(moduleName: String)
```

Overrides a module's loading to `required`.

---

### `loading()` - Custom Loading Rule

```kotlin
fun loading(rule: ModuleLoadingRuleValue, vararg moduleNames: String)
```

Sets custom loading rule for multiple modules.

**Example:**
```kotlin
moduleSet(commercialIdeBase()) {
  overrideAsEmbedded("intellij.rd.platform")
  overrideAsRequired("intellij.some.module")
  loading(ModuleLoadingRuleValue.EMBEDDED, "module.a", "module.b", "module.c")
}
```

---

## Data Classes

### `ContentModule`

Represents a module with optional loading attribute.

```kotlin
data class ContentModule(
  val name: String,
  val loading: ModuleLoadingRuleValue? = null,
  val includeDependencies: Boolean = false
)
```

### `ModuleSet`

Represents a named collection of content modules.

```kotlin
data class ModuleSet(
  val name: String,
  val modules: List<ContentModule>,
  val nestedSets: List<ModuleSet> = emptyList(),
  val alias: String? = null,
  val outputModule: String? = null,
  val selfContained: Boolean = false
)
```

### `DeprecatedXmlInclude`

Represents an XML include directive.

```kotlin
data class DeprecatedXmlInclude(
  val moduleName: String,
  val resourcePath: String,
  val ultimateOnly: Boolean = false,
  val optional: Boolean = false
)
```

### `TestPluginSpec`

Specification for a test plugin.

```kotlin
data class TestPluginSpec(
  val pluginId: String,
  val name: String,
  val pluginXmlPath: String,
  val spec: ProductModulesContentSpec,
  val additionalBundledPluginTargetNames: List<String> = emptyList(),
  val allowedMissingPluginIds: List<String> = emptyList()
)
```

---

## Loading Modes

| Mode | XML Attribute | Use Case |
|------|---------------|----------|
| `null` (default) | none | Regular modules |
| `EMBEDDED` | `loading="embedded"` | Core classloader modules |
| `REQUIRED` | `loading="required"` | Test framework modules |
| `OPTIONAL` | `loading="optional"` | Optional features |
| `ON_DEMAND` | `loading="on-demand"` | Lazy-loaded modules |

---

## Configuration Options

### `ModuleSetGenerationConfig`

Configuration class for module set generation with validation options.

#### `contentModuleAllowedMissingPluginDeps`

```kotlin
@JvmField val contentModuleAllowedMissingPluginDeps: Map<String, Set<String>> = emptyMap()
```

Map of content module name to set of allowed missing plugin dependency IDs.

Used to suppress validation errors for known issues where content modules have IML dependencies on plugin main modules but the XML declaration is intentionally missing.

**This is a temporary allowlist** - the goal is to eliminate all entries over time by either:
1. Adding the proper `<plugin id="..."/>` declaration to the content module XML
2. Removing the unnecessary IML dependency

**Example:**
```kotlin
ModuleSetGenerationConfig(
  contentModuleAllowedMissingPluginDeps = mapOf(
    "intellij.react.ultimate" to setOf("com.intellij.css"),
    "intellij.kotlin.gradle.multiplatform" to setOf("org.jetbrains.kotlin", "com.intellij.gradle"),
  ),
)
```

See [Validation Rules - Rule 7](validation-rules.md#rule-7-content-module-plugin-dependency-validation) for details on the validation this config suppresses.

---

## Related Documentation

- [Module Sets](module-sets.md) - How module sets work and best practices
- [Programmatic Content](programmatic-content.md) - Complete guide with examples
- [Test Plugins](test-plugins.md) - Test plugin generation
- [Validation Rules](validation-rules.md) - Validation and error handling
- [Migration Guide](migration-guide.md) - Migrating from XML to DSL
