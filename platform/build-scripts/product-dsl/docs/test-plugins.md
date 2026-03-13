# Test Plugin Generation

Test plugins are special plugins that provide test framework modules for running tests.
Unlike regular products, test plugins have plugin.xml in test resources (`testResources/META-INF/`).

## Design Principle

Test plugins are part of the product specification, defined inside `getProductContentDescriptor()`.
This follows the same pattern as module sets - everything flows through the product content spec.

In dev mode, tests run with `product + bundled production plugins + test plugin`. The test plugin acts as a
container for all content modules required by test-scope dependencies, so its content must be complete
without relying on other test plugins.

For full DSL reference (`moduleSet()`, `module()`, etc.), see [dsl-api-reference.md](dsl-api-reference.md).

## Usage

```kotlin
override fun getProductContentDescriptor() = productModules {
  // ... product content ...

  testPlugin(
    pluginId = "intellij.python.junit5Tests.plugin",
    name = "Python Tests Plugin for intellij.python.junit5Tests module",
    pluginXmlPath = "python/junit5Tests/plugin/testResources/META-INF/plugin.xml"
  ) {
    // Only specify modules not already provided by the product/module sets.
    // Auto-add will pull in transitive test deps into the "additional" region.
    moduleSet(CommunityModuleSets.platformTestFrameworksCore())

    module("intellij.tools.testsBootstrap")
    module("intellij.python.testFramework")
    // ... additional test modules
  }
}
```

## Conditional Bundled Plugins

If a test plugin depends on content from plugins that are bundled only under runtime flags,
use `additionalBundledPluginTargetNames` to treat those plugin modules as available for auto-add and validation.
These are plugin target (JPS module) names, not plugin IDs.

```kotlin
testPlugin(
  pluginId = "intellij.rider.test.cases.qodana.plugin",
  name = "Rider Tests Plugin for intellij.rider.test.cases.qodana module",
  pluginXmlPath = "rider/test/cases-qodana/plugin/resources/META-INF/plugin.xml",
  additionalBundledPluginTargetNames = listOf("intellij.dependencyAnalysis")
) {
  // ...
}
```

## Allow Missing Plugin Dependencies

If a DSL test plugin depends on a plugin that is not resolvable in the test plugin scope, the dependency planner reports an error and skips the plugin dependency. To suppress this error for known/expected cases, list those plugin IDs in `allowedMissingPluginIds`.

```kotlin
testPlugin(
  pluginId = "intellij.some.tests.plugin",
  name = "Some Tests Plugin",
  pluginXmlPath = "path/to/testResources/META-INF/plugin.xml",
  allowedMissingPluginIds = listOf("com.intellij.java")
) {
  // ...
}
```

For per-module suppression (to keep it close to the module that triggers the auto-add), pass
`allowedMissingPluginIds` to `module()`, `embeddedModule()`, or `requiredModule()` inside the test plugin block:

```kotlin
testPlugin(
  pluginId = "intellij.some.tests.plugin",
  name = "Some Tests Plugin",
  pluginXmlPath = "path/to/testResources/META-INF/plugin.xml",
) {
  requiredModule("intellij.some.tests.framework", allowedMissingPluginIds = listOf("com.intellij.java"))
}
```

This only suppresses unresolvable dependency errors; it does not add the dependency.
Test plugin allowlists are DSL-only; suppressions.json does not include test plugin allowlists.

## Test Plugin Detection

Plugins extracted from plugin.xml are detected as **test plugins** based on their content modules. DSL-defined test plugins (`testPlugin {}`) are always treated as test plugins even if they don't declare test framework modules.
A plugin is a test plugin if it declares any test framework module in its `<content>` block.

### Test Framework Content Modules

The following modules mark a plugin as a test plugin when declared as content:

```kotlin
testFrameworkContentModules = setOf(
  "intellij.libraries.junit4",
  "intellij.libraries.junit5",
  "intellij.libraries.junit5.jupiter",
  "intellij.libraries.junit5.launcher",
  "intellij.libraries.junit5.params",
  "intellij.libraries.junit5.vintage",
  "intellij.platform.testFramework",
  "intellij.platform.testFramework.common",
  "intellij.platform.testFramework.core",
  "intellij.platform.testFramework.impl",
  "intellij.tools.testsBootstrap",
)
```

### Detection Logic

```kotlin
// From PluginGraphBuilder.addPluginWithContent(...)
val isTestPlugin = content.isTestPlugin ||
                   (testFrameworkContentModules.isNotEmpty() && contentModules.any { it in testFrameworkContentModules })
```

**Key implications**:
- Test plugins' content modules do NOT satisfy production plugin dependencies
- Test plugins use graph bundling edges (`EDGE_BUNDLES_TEST`) instead of a separate product map
- Discovered test plugins use `forTestPlugin` (module sets + all bundled plugins); DSL test plugins use `forDslTestPlugin` to exclude other test plugins from resolution

## DSL-Defined vs Discovered Test Plugins

### DSL-Defined Test Plugins

Created via `testPlugin {}` in `getProductContentDescriptor()`:
- Plugin XML is **auto-generated** from Kotlin DSL
- The generated file is fully owned by the DSL generator (`TestPluginXmlGenerator`): existing `plugin.xml`
  content is replaced on regeneration, and dependency-updater region semantics do not apply
- `isDslDefined = true` in `PluginContentInfo`
- Auto-fixes (like structural violation fixes) are **skipped** - fix in Kotlin instead

### Discovered Test Plugins

Manually created plugins with handwritten plugin.xml:
- Configured via `testPluginsByProduct` in `ModuleSetGenerationConfig`
- `isDslDefined = false` in `PluginContentInfo`
- Auto-fixes can be applied to plugin.xml

```kotlin
// In ultimateGenerator.kt
testPluginsByProduct = mapOf(
  "idea" to setOf(
    "intellij.idea.ultimate.tests.devBuildTests.plugin",
  ),
  "CLion" to setOf(
    "intellij.clion.dev.build.tests.plugin",
  ),
  "Rider" to setOf(
    "intellij.rider.plugins.oss.test.plugin",
    "intellij.rider.test.cases.rdct.plugin",
  ),
)
```

## Automatic Dependency Addition

DSL-defined test plugins support **automatic addition** of JPS dependencies that weren't explicitly declared.

### How It Works

When a test plugin's content modules have JPS module dependencies with descriptors, the generator checks if those dependencies are **resolvable**. It considers production runtime, test runtime, and PROVIDED scopes for test plugins.

- **Resolvable** = available in the same product: module sets + bundled production plugin content + `additionalBundledPluginTargetNames` (target names; other test plugins excluded)
- **Unresolvable** = not found anywhere

For DSL test plugins, JPS dependency targets also support a test-descriptor fallback: if target `X` has no descriptor `X.xml` but has `X._test.xml`, auto-add treats the dependency as `X._test`.

Only **unresolvable** modules are auto-added to the test plugin content.

**Note:** Content modules that belong to a plugin are **not** auto-added when their owning plugin is resolvable for the test plugin scope (the module is already available). If the owning plugin is not resolvable, the generator emits an error and skips auto-add unless the plugin ID is listed in `allowedMissingPluginIds` (either on the test plugin or on the module that triggered the dependency). **Exception:** library wrapper modules (`intellij.libraries.*`) are always auto-added as module dependencies, even if owned by a plugin.

### Source of Truth and Transitive Closure

Auto-add uses **PluginGraph** as the single source of truth for module descriptors and resolvable modules, but reads JPS dependencies from the declared content modules. Project library dependencies are mapped to library modules via `ModuleSetGenerationConfig.projectLibraryToModuleMap` (built from JPS library modules, not the graph), so library modules don't need to be present in module sets to be discovered.

- Walk **transitive** dependencies (A -> B -> C) for test plugin content.
- For JPS **library** dependencies, map library name -> library module via `projectLibraryToModuleMap` and include those library modules in the closure.
- For DSL test plugins, ignore `libraryModuleFilter`: the test plugin is a container for all required modules in dev mode, so needed library modules must be included even if products filter them elsewhere.
- Auto-added modules are merged into the generated test plugin content (written under the `<!-- region additional -->` block), so a second generator run stays clean.

### Why Module Sets Don't Need Special Handling

Module sets are just convenience for avoiding code duplication - they're NOT special. The auto-add logic respects them naturally:

```kotlin
// If the product already includes librariesTestFrameworks(), don't add it here.
// The modules are resolvable via product content, so auto-add will skip them.
// moduleSet(CommunityModuleSets.librariesTestFrameworks())

// If a module is not resolvable in the product, it WILL be auto-added (under "additional").
// module("intellij.platform.jewel.intUi.standalone")  // Can be omitted
```

### Practical Implications

1. **Minimal DSL needed** - Only specify module sets used by the product; individual modules auto-resolve
2. **No redundant declarations** - Don't add modules (or library module sets) already present in product module sets
3. **Catches missing deps** - Modules with descriptors but not in product module sets get auto-added

See [dependency_generation.md](dependency_generation.md) for implementation details.

## Key Differences from Products

| Aspect | Products | Test Plugins |
|--------|----------|--------------|
| plugin.xml location | `resources/META-INF/` | `testResources/META-INF/` |
| Module set handling | xi:include or inline | Always inlined |
| Structure | Full product spec | id/name/vendor + content |
| Dependency resolution | `forProductionPlugin` predicate | `forTestPlugin` predicate (discovered) / `forDslTestPlugin` (DSL) |
| Content modules | Satisfy other plugin deps | Don't satisfy production deps |

## Generated XML

```xml
<!-- DO NOT EDIT: This file is auto-generated from Kotlin code -->
<!-- To regenerate, run 'Generate Product Layouts' or directly UltimateGenerator.main() -->
<!-- Source: ...Properties.getProductContentDescriptor() -->
<idea-plugin>
  <id>intellij.python.junit5Tests.plugin</id>
  <name>Python Tests Plugin for intellij.python.junit5Tests module</name>
  <vendor>JetBrains</vendor>

  <content namespace="jetbrains">
    <!-- region platform.testFrameworks.core -->
    <module name="intellij.platform.testFramework"/>
    <!-- endregion -->

    <!-- region additional -->
    <module name="intellij.tools.testsBootstrap"/>
    <module name="intellij.libraries.junit5"/>
    <module name="intellij.libraries.junit5.jupiter"/>
    <!-- endregion -->
  </content>
</idea-plugin>
```

## Validation

Test plugins follow the same validation rules as production plugins (see [validation-rules.md](validation-rules.md#rule-5-plugin-dependency-validation)). The key differences are:

| Aspect | Production Plugins | Test Plugins |
|--------|-------------------|--------------|
| Bundling tracking | Graph `EDGE_BUNDLES` | Graph `EDGE_BUNDLES_TEST` |
| Resolution predicate | `forProductionPlugin` | `forTestPlugin` (discovered) / `forDslTestPlugin` (DSL) |
| Resolution scope | Module sets + ALL bundled plugins | Module sets + ALL bundled plugins (discovered); DSL test plugins: module sets + bundled production plugins + self |

### Why Test Plugin Content Doesn't Satisfy Production Dependencies

Test plugin content modules are excluded from production plugin resolution to prevent:
1. Circular dependencies (test plugin depends on production plugin being tested)
2. Production code accidentally depending on test framework modules
3. Test modules leaking into production distributions

## Debugging

- Run the generator with `--log=dslTestDeps` to see auto-add/skip decisions for DSL test plugins.
- Add `debug("dslTestDeps") { "..." }` statements near dependency processing; they are silent unless `--log` enables the tag.

## Available Module Sets for Test Plugins

These module sets are designed for test plugin content. Use them only when the product
*does not already* include the same modules; otherwise prefer the auto-add logic.

```kotlin
// Test framework libraries
CommunityModuleSets.librariesTestFrameworks()
CommunityModuleSets.librariesTestFrameworksExtended()

// Platform test frameworks
CommunityModuleSets.platformTestFrameworksCore()
CommunityModuleSets.platformTestFrameworksJunit5()
CommunityModuleSets.platformTestFrameworksIjent()
```

## See Also

- [dsl-api-reference.md](dsl-api-reference.md#testplugin----define-test-plugin) - `testPlugin {}` DSL reference
- [validation-rules.md](validation-rules.md#rule-5-plugin-dependency-validation) - Validation rules
- `.claude/rules/product-dsl.md` - Debug flags for generator runs (use `--log` tags)
- [programmatic-content.md](programmatic-content.md) - Content specification guide
