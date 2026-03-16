# Quick Start Guide

Get started with product-dsl in 5 minutes.

## Prerequisites

- IntelliJ IDEA with project loaded
- Familiarity with Kotlin basics

## Common Tasks

### Task 1: Add a Module to an Existing Module Set

**Scenario**: Add `intellij.platform.new.feature` to the `essential` module set.

1. **Open the module set file**:
   ```
   community/platform/build-scripts/src/org/jetbrains/intellij/build/productLayout/CommunityModuleSets.kt
   ```

2. **Find the module set function** and add your module:
   ```kotlin
   fun essential(): ModuleSet = moduleSet("essential", includeDependencies = true) {
     // ... existing modules ...
     
     module("intellij.platform.new.feature")  // Add your module
   }
   ```

3. **Run the generator**:
   - IDE: Run configuration **"Generate Product Layouts"**
   - Or: `bazel run //platform/buildScripts:plugin-model-tool`

4. **Commit the changes** (both Kotlin and generated XML).

---

### Task 2: Create a New Module Set

**Scenario**: Create a new `myFeature` module set.

1. **Add a function** to `CommunityModuleSets.kt`:
   ```kotlin
   /**
    * My feature modules.
    * Provides X functionality for Y products.
    */
   fun myFeature(): ModuleSet = moduleSet("myFeature") {
     embeddedModule("intellij.myFeature.core")
     module("intellij.myFeature.impl")
     module("intellij.myFeature.ui")
   }
   ```

2. **Run the generator** to create XML:
   ```
   Run "Generate Product Layouts"
   ```

3. **Use in a product** (see Task 3).

---

### Task 3: Add a Module Set to a Product

**Scenario**: Include `myFeature()` in GoLand.

1. **Open the product properties file**:
   ```
   platform/buildScripts/src/productLayout/GoLandProperties.kt
   ```

2. **Add to `getProductContentDescriptor()`**:
   ```kotlin
   override fun getProductContentDescriptor() = productModules {
     // ... existing module sets ...
     
     moduleSet(myFeature())  // Add your module set
   }
   ```

3. **Run the generator**.

---

### Task 4: Fix a Validation Error

**Scenario**: "Missing dependency" error during generation.

```
Product: GoLand
  ✗ Missing: 'intellij.platform.symbols'
    Needed by: intellij.platform.vcs.impl
    Suggestion: Add module set: symbols
```

**Fix options** (in order of preference):

1. **Add the suggested module set**:
   ```kotlin
   moduleSet(symbols())  // In product's getProductContentDescriptor()
   ```

2. **Add the individual module**:
   ```kotlin
   module("intellij.platform.symbols")
   ```

3. **Allow missing** (temporary/special cases only):
   ```kotlin
   allowMissingDependencies("intellij.platform.symbols")
   ```

---

### Task 5: Override Loading Mode

**Scenario**: Make a module embedded in a specific product.

```kotlin
override fun getProductContentDescriptor() = productModules {
  moduleSet(essential()) {
    overrideAsEmbedded("intellij.platform.specific.module")
  }
}
```

**Note**: Overrides cause the module set to be inlined (no xi:include).

---

## Quick Reference

### Loading Modes

| Mode | DSL Function | Use Case |
|------|--------------|----------|
| Default | `module()` | Regular modules |
| Embedded | `embeddedModule()` | Core classloader |
| Required | `requiredModule()` | Test frameworks |

### Common Module Sets

| Set | Description |
|-----|-------------|
| `essential()` | Core IDE modules |
| `vcs()` | Version control |
| `xml()` | XML support |
| `debugger()` | Debugger platform |
| `ideCommon()` | Full IDE common |
| `ideUltimate()` | Ultimate base |

### Run Commands

| Method | Command |
|--------|---------|
| IDE | Run "Generate Product Layouts" |
| Bazel | `bazel run //platform/buildScripts:plugin-model-tool` |
| JSON | `--json` flag for analysis output |

---

## Troubleshooting

### "Module set not appearing in generated XML"

**Check**: Is your function public and returning `ModuleSet`?
```kotlin
fun mySet(): ModuleSet = moduleSet("my.set") { ... }  // ✓
private fun mySet() = ...  // ✗ Not discovered
```

### "Duplicate module" error

**Check**: Is the module in multiple sets?
```bash
# Search for the module name
grep -r "intellij.duplicate.module" community/platform/build-scripts/
```

### "Invalid loading override"

**Check**: Are you overriding a direct module (not nested)?
```kotlin
// ✗ Wrong: nested module override
moduleSet(parentSet()) {
  overrideAsEmbedded("module.from.nested.set")  // Error!
}

// ✓ Correct: reference nested set directly
moduleSet(nestedSet()) {
  overrideAsEmbedded("module.from.nested.set")  // OK
}
```

---

## Next Steps

- [Module Sets Guide](module-sets.md) - Deep dive into module sets
- [DSL API Reference](dsl-api-reference.md) - Complete function reference
- [Validation Rules](validation-rules.md) - Understand validation
- [Architecture Overview](architecture-overview.md) - System architecture
