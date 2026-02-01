# Error Reference

Complete reference of validation errors, their causes, and fixes.

## Error Type Index

| Error Type | Severity | Auto-Fix | Description |
|------------|----------|----------|-------------|
| [FileDiff](#filediff) | Warning | Yes | Generated file differs from disk |
| [MissingDependenciesError](#missing-dependency-in-product) | Error | No | Product missing required modules |
| [SelfContainedValidationError](#self-contained-set-validation) | Error | No | Self-contained set has external deps |
| [DuplicateModulesError](#duplicate-content-modules) | Error | No | Same module in multiple sets |
| [PluginDependencyError](#plugin-dependency-errors) | Error | Partial | Plugin has unresolvable dependencies |
| [PluginDependencyNotBundledError](#plugin-to-plugin-dependency-error) | Error | No | Plugin depends on another plugin not bundled in same product |
| [XIncludeResolutionError](#xinclude-resolution-error) | Error | No | xi:include path cannot be resolved |
| [MissingModuleSetsError](#missing-module-sets) | Error | No | Referenced module set not found |
| [Structural Violations](#structural-loading-violations) | Error | Yes* | Loading mode constraint violations |
| [MissingContentModulePluginDep](#missing-content-module-plugin-dependency) | Error | No | Content module missing plugin dep |
| [MissingTestPluginPluginDep](#missing-test-plugin-plugin-dependency) | Error | No | Test plugin missing plugin dep |
| [DSL Constraint Errors](#dsl-constraint-errors) | Error | No | Invalid DSL usage |
| [Suppressible Errors](#suppressible-errors) | Warning | Yes | Errors detected during generation |

---

## FileDiff

Generated file content differs from what's on disk.

```
File diff detected: community/platform/platform-resources/generated/META-INF/intellij.moduleSets.vcs.xml
  Change type: MODIFY
```

**Cause**: Running "Generate Product Layouts" detected changes that need to be applied.

**Fix**: The generator auto-applies diffs. Commit the changes or run the generator again.

---

## Missing Dependency in Product

```
Product: GoLand

  ✗ Missing: 'intellij.platform.polySymbols'
    Needed by: intellij.platform.vcs.impl
    Suggestion: Add module set: symbols
```

**Cause**: A module in the product depends on another module that isn't included.

**Fixes** (in order of preference):
1. **Add the suggested module set** to the product's `getProductContentDescriptor()`
2. **Add individual module** via `module()` or `embeddedModule()` if no set exists
3. **For cross-plugin deps** with non-critical loading → add plugin to `knownPlugins`

---

## Self-Contained Set Validation

```
❌ Module set 'core.platform' is marked selfContained but has unresolvable dependencies

  ✗ Missing: 'fleet.kernel'
    Needed by: intellij.platform.kernel

💡 To fix:
1. Add the missing modules/sets to 'core.platform'
2. Or remove selfContained=true if this set composes with others
```

**Cause**: A module set with `selfContained = true` has dependencies outside the set.

**Fixes**:
1. **Add missing modules** to the set (preferred for truly standalone sets)
2. **Remove `selfContained = true`** if the set is designed to compose with others

---

## Duplicate Content Modules

```
❌ ERROR: Duplicate modules found across 3 module set(s)

  ✗ Module 'intellij.platform.vcs.impl' appears in:
      - vcs
      - ide.common
    → Suggested fix: Remove from: ide.common

📋 Each module must belong to exactly one module set.
```

**Cause**: Same module is declared in multiple module sets or added via both `moduleSet()` and `module()`.

**Runtime impact**: Causes "Plugin has duplicated content modules declarations" error.

**Fix**: Remove duplicate declarations. Keep module in only one set.

---

## Plugin Dependency Errors

### Unresolvable Module Dependency

```
❌ Plugin 'intellij.settingsRepository' has unresolvable content module dependencies

  ✗ Missing: 'intellij.libraries.sshd.osgi' (not in any known plugin or module set)
    Needed by:
      └─ plugin.xml

Proposed patch: Add allowMissingDependencies to the product spec.
--- a/build/src/org/jetbrains/intellij/build/IdeaUltimateProperties.kt
+++ b/build/src/org/jetbrains/intellij/build/IdeaUltimateProperties.kt
@@ -379,1 +379,4 @@
 override fun getProductContentDescriptor(): ProductModulesContentSpec = productModules {
+  allowMissingDependencies(
+    "intellij.libraries.sshd.osgi",
+  )
```

**Fixes**:
- **If dependency should be available**: Declare as `<content>` in a plugin, or add to module set
- **If plugin isn't bundled**: This may be expected behavior

### Filtered Dependency

```
Plugin 'intellij.micronaut' has unresolvable content module dependencies

  * Missing: 'intellij.javaee.jpa' (auto-inferred JPS dependency, filtered by config)
    Needed by:
      └─ intellij.micronaut.data (content module)
```

**Cause**: Dependency was auto-inferred from JPS but filtered or suppressed (e.g., by `suppressions.json` or allowed-missing config).

**Fixes**:
1. Add to `pluginAllowedMissingDependencies` in config
2. Or add explicit `<module name="..."/>` to content module descriptor
3. If intentionally suppressed in `suppressions.json`, no error should be reported; if it is, treat as validator bug

### Per-Product Breakdown

```
❌ Plugin 'intellij.myPlugin' has unresolvable content module dependencies

  ✗ Missing: 'intellij.some.module'
    Unresolved in products:
      └─ GoLand (not bundled)
      └─ CLion (not bundled)
```

**Fix**: Either bundle the dependency plugin in those products, or ensure it's in a module set.

---

## Plugin-to-Plugin Dependency Error

```
❌ Plugin 'intellij.foo' has unresolvable plugin dependencies

  * Unresolved plugin IDs:
    * com.intellij.bar
  * Missing in products:
    * GoLand: com.intellij.bar
```

**Cause**: A plugin declared a dependency on another plugin that is not bundled in the same product, or the dependency plugin cannot be resolved to a plugin node in the graph.

**Fixes**:
1. **Bundle the dependency plugin** in the same products as the depending plugin
2. **Remove the dependency** if it's no longer required
3. **Ensure the dependency plugin has a valid `plugin.xml`** so it registers in the graph

---

## Structural Loading Violations

Emitted by `PluginContentStructureValidator` (ruleName `pluginContentStructureValidation`).

```
❌ Plugin 'intellij.myPlugin' has structural violations

  ✗ intellij.my.required.module (REQUIRED) depends on:
      └─ intellij.my.optional.module (OPTIONAL) - violates loading hierarchy
```

**Cause**: Module loading constraints are violated:

| Depending Module | Cannot Depend On | Reason |
|------------------|------------------|--------|
| `EMBEDDED` | Non-EMBEDDED siblings | Embedded modules merge into main JAR |
| `REQUIRED` | `OPTIONAL`/`ON_DEMAND` siblings | Makes optional effectively required |

**Auto-Fix**: For non-DSL plugins, `PluginContentStructureValidator` automatically adds `loading="required"` to the dependency. See [StructuralViolationFix](#auto-fix-structural-violations).

**Manual Fix** (for DSL-defined plugins): Update the module set definition to use `requiredModule()` instead of `module()`.

---

## XInclude Resolution Error

```
❌ xi:include resolution failed

  Plugin: intellij.myPlugin
  Path: META-INF/NonExistent.xml
  Debug: Searched in: [module1, module2]
```

**Cause**: An xi:include directive references a resource that doesn't exist.

**Fixes**:
1. **Create the missing file** at the expected location
2. **Fix the path** in the xi:include directive
3. **Remove the xi:include** if no longer needed
4. **Use `optional = true`** in `deprecatedInclude()` if file may not exist

---

## Missing Module Sets

```
❌ Product references missing module sets

  ✗ Missing: 'nonexistent.set'
```

**Cause**: Product references a module set that doesn't exist.

**Fix**: Either create the module set or remove the reference.

---

## DSL Constraint Errors

### Invalid Loading Overrides

```
❌ Invalid loading overrides for module set 'commercialIdeBase'

The following 2 module(s) are not direct modules of this set:
  ✗ intellij.rd.platform
  ✗ intellij.rd.ui

Note: You cannot override nested set modules.

💡 Hint: You can only override direct modules, not modules from nested sets.
   To override modules from nested sets, reference the nested set directly:

   moduleSet(YourNestedSet()) {
     overrideAsEmbedded("module.name")
   }
```

**Cause**: Attempting to override loading mode for modules in nested sets.

**Fix**: Reference the nested set directly and apply overrides there.

### Duplicate Module Alias

```
❌ Duplicate module alias detected: 'com.intellij.modules.xml'

  Already defined in: module set 'xml'
  Attempted to redefine at: product level

💡 Hint: Module aliases must be unique across the entire product.
```

**Cause**: Same alias declared in multiple places.

**Fix**: Remove duplicate alias declaration.

### Redundant Module Set References

```
❌ Product specification errors: Redundant module set references detected

  ✗ Product 'GoLand': module set 'ssh' is redundant (already nested in 'ide.ultimate')

💡 Hint: Remove redundant module sets from product's getProductContentDescriptor() method.

   Example fix:
   override fun getProductContentDescriptor() = productModules {
     // moduleSet(ssh())           // ← REMOVE (already in ide.ultimate)
     moduleSet(ideUltimate())       // ← KEEP (includes ssh)
   }
```

**Cause**: Product explicitly references a module set that's already nested in another set it uses.

**Fix**: Remove the redundant `moduleSet()` call.

---

## Auto-Fix: Structural Violations

The generator can automatically fix structural loading violations in **non-DSL plugins** (those with handwritten plugin.xml files).

**How it works**:
1. Detection: `PluginContentStructureValidator` identifies loading constraint violations
2. Fix: `fixStructuralViolations()` in `PluginContentStructureValidator` modifies plugin.xml to add `loading="required"`
3. Output: Generates a diff that's auto-applied during generation

**Example auto-fix**:
```xml
<!-- Before -->
<module name="intellij.my.optional.module"/>

<!-- After (auto-fixed) -->
<module name="intellij.my.optional.module" loading="required"/>
```

**Note**: DSL-defined plugins are skipped (their XML is auto-generated). Fix the module set definition instead.

---

## Library Module Dependency Violation

```
Module intellij.platform.ide.impl depends on library 'Guava' directly.
Use library module 'intellij.libraries.guava' instead.
```

**Auto-Fix**: The generator modifies the `.iml` file:
```xml
<!-- Before -->
<orderEntry type="library" name="Guava"/>

<!-- After (auto-fixed) -->
<orderEntry type="module" module-name="intellij.libraries.guava"/>
```

---

## Test Library in Production Scope

```
Module intellij.platform.ide.impl has testing libraries in production scope:
  - JUnit5 (COMPILE) → should be TEST scope
Run 'Generate Product Layouts' to fix automatically.
```

**Auto-Fix**: Run "Generate Product Layouts" to move test libraries to TEST scope.

---

## Missing Content Module Plugin Dependency

```
❌ Content module 'intellij.react.ultimate' has IML dependencies on plugin main modules
   but is missing corresponding XML plugin declarations:

  ✗ Missing: <plugin id="com.intellij.css"/>
    IML dependency: intellij.css (main module of com.intellij.css)

💡 Fix: Add <plugin id="com.intellij.css"/> to the content module's XML descriptor

Or suppress temporarily: Add to contentModuleAllowedMissingPluginDeps in ModuleSetGenerationConfig:
       "intellij.react.ultimate" to setOf("com.intellij.css"),
```

**Cause**: Content module has compile dependency in `.iml` on a plugin's main module, but the module's XML descriptor doesn't declare the plugin dependency.

**Runtime impact**: `NoClassDefFoundError` when the content module tries to use classes from the plugin.

**Fixes**:
1. **Add XML declaration** (preferred): Add `<plugin id="..."/>` to the content module's descriptor
2. **Remove IML dependency**: If the dependency isn't actually needed
3. **Suppress temporarily**: Add to `contentModuleAllowedMissingPluginDeps` config

**Note**: The error message includes copy-paste Kotlin code for the suppression config.

---

## Missing Test Plugin Plugin Dependency

```
❌ Test plugin 'intellij.rider.tests' is missing plugin dependencies required by its content modules

  ✗ Missing: com.jetbrains.codeWithMe
    Needed by: intellij.rider.test.cases.rdct.distributed._test

💡 Fix: Add <plugin id="com.jetbrains.codeWithMe"/> to the test plugin's plugin.xml
```

**Cause**: A DSL-defined test plugin has content modules whose JPS dependencies include modules
owned by production plugins that are resolvable in test scope, but the test plugin's plugin.xml
doesn't declare the required `<plugin id="..."/>` dependency.

**Runtime impact**: `NoClassDefFoundError` in tests because classes from the owning plugin are
missing from the test classpath.

**Fixes**:
1. **Declare plugin dependency** (preferred): Ensure the test plugin generator emits `<plugin id="..."/>`.
2. **Adjust JPS deps**: Remove the JPS dependency if it shouldn't be required at runtime.
3. **Suppress intentionally**: Add the plugin ID to `allowedMissingPluginIds` in the test plugin spec
   (or module-level `allowedMissingPluginIds` for a narrower scope).

---

## Suppressible Errors

These errors are detected during generation and can be suppressed via `suppressedErrors` in `suppressions.json`.

Unlike validation errors that check constraints across the model, these errors report issues found during the actual generation process (e.g., when parsing module descriptors).

### Non-Standard Descriptor Root

```
Error [NON_STANDARD_DESCRIPTOR_ROOT]

  * Descriptor uses non-standard XML root element
    Module: intellij.fullLine.yaml
    Path: .../intellij.fullLine.yaml.xml

To suppress: Add to suppressedErrors in suppressions.json:
  "nonStandardRoot:intellij.fullLine.yaml"
```

**Cause**: Module descriptor XML uses `<dependencies>` as root element instead of standard `<idea-plugin>`.

The dependency generator expects `<idea-plugin>` as the root element. Descriptors with non-standard roots (like `<dependencies>`) cannot be parsed for dependency generation.

**Fixes**:
1. **Convert to standard format** (preferred): Change root element to `<idea-plugin>`
2. **Suppress if intentional**: Add suppression key to `suppressedErrors` in `suppressions.json`

**Suppression key format**: `nonStandardRoot:{moduleName}`

**Example suppression** (in `suppressions.json`):
```json
{
  "suppressedErrors": [
    "nonStandardRoot:intellij.cmake.psi",
    "nonStandardRoot:intellij.fullLine.yaml"
  ]
}
```

---

## Investigation Tools

Use Plugin Model Analyzer MCP to debug dependency issues:

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

// Get module info including products/sets
mcp__PluginModelAnalyzer__get_module_info(
  moduleName = "intellij.fullLine.cpp"
)
```

---

## Investigation Strategy

1. **Identify dependency chain**: Use `find_dependency_path` to understand why dep is needed
2. **Check if cross-plugin**: Is missing module in non-bundled plugin? Check source module's loading
3. **Find right fix level**:
   - Missing module set → add nested set
   - Product missing set → add to product
   - Cross-plugin with non-critical loading → add to `knownPlugins`
   - Missing infrastructure → add to module set
4. **Verify**: Run "Generate Product Layouts" again

---

## See Also

- [validation-rules.md](validation-rules.md) - Validation rules and architecture
- [docs/validators/README.md](validators/README.md) - Validator specs
- [dsl-api-reference.md](dsl-api-reference.md) - DSL function reference
- [module-sets.md](module-sets.md) - Module set documentation
