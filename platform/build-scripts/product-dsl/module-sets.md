# Module Sets

## What Are Module Sets?

Module sets are **reusable collections of modules** that can be referenced as a single entity in product configurations. They solve the problem of:
- **Duplication**: Avoiding repetitive module lists across products
- **Maintainability**: Changing a module set once updates all products that use it
- **Organization**: Grouping related modules by functionality (e.g., VCS, XML, essential platform)
- **Composition**: Building complex products from simple, composable building blocks

## How Module Sets Work

Module sets are **defined in Kotlin code** and **auto-generate XML files**:

1. **Define**: Write Kotlin functions in `CommunityModuleSets.kt` or `UltimateModuleSets.kt`
2. **Generate**: Run the generator to create XML files from Kotlin definitions
3. **Use**: Products reference module sets via `moduleSet()` in their content descriptors

**Key principle**: Code is the source of truth, XML files are generated artifacts.

## Module Set Locations

- **Community module sets**: `community/platform/build-scripts/product-dsl/src/CommunityModuleSets.kt`
- **Ultimate module sets**: `platform/buildScripts/src/productLayout/UltimateModuleSets.kt`

Each module set is defined as a Kotlin function (e.g., `fun essential(): ModuleSet`) that returns a `ModuleSet` object. The XML files (pattern: `intellij.moduleSets.<name>.xml`) are **auto-generated** from this Kotlin code.

### Generating XML Files

To regenerate XML files from Kotlin code:

**Using JetBrains MCP (Recommended):**
```kotlin
mcp__jetbrains__execute_run_configuration(configurationName="Generate Product Layouts")
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

## Discovering Available Module Sets

To find available module sets and understand their contents:

1. **Browse the source code** - Open the Kotlin files to see all module set functions:
   - `community/platform/build-scripts/product-dsl/src/CommunityModuleSets.kt`
   - `platform/buildScripts/src/productLayout/UltimateModuleSets.kt`
   
2. **Read KDoc comments** - Each module set function has comprehensive documentation including:
   - What it contains
   - When to use it
   - Example products using it
   - Relationships to other module sets

3. **Use the JSON endpoint** - Run `UltimateModuleSets.main(args = ["--json"])` for programmatic analysis:
   - Complete module composition
   - Nesting hierarchy
   - Product usage
   - Module distribution across sets

4. **Check generated XML** - The generated XML files show expanded module lists:
   - Pattern: `intellij.moduleSets.<name>.xml`
   - Locations: `community/platform/platform-resources/generated/META-INF/` (community) or `licenseCommon/generated/` (ultimate)

**Best practice**: Start by browsing the Kotlin source files and reading KDoc comments. They are the canonical source of truth.

## Best Practices

### When to Create a New Module Set

Create a new module set when:
- **Multiple products** need the same group of modules
- The modules form a **cohesive functional unit** (e.g., VCS support, XML support, SSH support)
- You want to **enforce consistency** across products using these modules
- The group is likely to be **reused or evolved** over time

Don't create a module set if:
- Only **one product** needs these specific modules
- The modules are **product-specific** customizations
- The grouping is **arbitrary** without functional cohesion

### Naming Conventions

- Use **functional names** that describe what the modules do: `vcs`, `xml`, `ssh`, `essential`
- Use **dot notation** for hierarchical relationships: `libraries.core`, `ide.common`, `essential.minimal`
- Avoid **product names** in module set names (sets should be reusable)
- Keep names **concise** and **memorable**

### Module Set Composition

Module sets can:
- **Include individual modules**: `module("intellij.platform.vcs.impl")`
- **Nest other module sets**: `moduleSet(corePlatform())`
- **Use embedded loading**: `embeddedModule("intellij.platform.core")` for core classloader
- **Include dependencies**: `includeDependencies = true` to automatically pull in module dependencies

**Tip**: Prefer nesting existing module sets over duplicating modules. This creates a clean hierarchy and ensures consistency.

### Content Modules vs Implementation Modules

**Critical distinction** for understanding `includeDependencies`:

#### Content Modules
- **Have XML descriptors** (e.g., `resources/fleet.andel.xml`)
- Define extensions, services, components, listeners
- **Must** be declared in module sets or product content descriptors
- Cannot be loaded via `productImplementationModules`
- Example: `fleet.andel`, `fleet.rpc`, `intellij.platform.vcs.impl`

#### Implementation Modules
- **No XML descriptors** - just code (classes, resources)
- Provide implementation classes loaded into classloader
- Can be in `productImplementationModules` OR loaded via `includeDependencies`
- Example: `fleet.util.multiplatform`, `fleet.backend`, `intellij.platform.webide.impl`

**To check if a module has a descriptor:**
```bash
# Using JetBrains MCP
find_files_by_glob("**/moduleName.xml")

# Or check the module's resources directory
ls community/modulePath/resources/  # Look for .xml files
```

### How `includeDependencies` Works

When you declare `embeddedModule("someModule", includeDependencies = true)`:

1. **Collects ALL transitive JPS dependencies** (not just direct!)
   - Uses BFS traversal through the full dependency graph
   - Example: `fleet.andel` → `fleet.util.core` → `fleet.multiplatform.shims` → etc.

2. **Filters to implementation modules only**
   - Includes ONLY modules WITHOUT XML descriptors
   - Content modules are assumed to be in module sets already
   - This prevents duplication of content modules

3. **Loads into classloader**
   - Implementation code becomes available at runtime
   - No plugin descriptor processing (since there are no descriptors)

**Example:**
```kotlin
embeddedModule("fleet.andel", includeDependencies = true)
```

This automatically includes these implementation dependencies:
- `fleet.util.multiplatform` (no descriptor)
- Plus any other transitive implementation modules

But does NOT duplicate these content modules (already in `fleetMinimal()`):
- `fleet.util.core` (has descriptor)
- `fleet.bifurcan` (has descriptor)
- etc.

**Best practice**: Use `includeDependencies = true` for modules with implementation-only transitive dependencies to avoid explicit listing in `productImplementationModules`.

## Module Set Validation

### Two-Tier Validation System

The build system uses a **two-tier validation approach** to ensure module dependencies are resolvable while avoiding false positives:

#### Tier 1: Product-Level Validation

**What it validates**: All products with their complete module composition

**How it works**:
- Collects all modules from a product's complete module set hierarchy
- Validates that each module's dependencies are available within that product context
- Reports errors per-product with clear affected modules

**Why this tier exists**: Products are the actual deployment units. Most module sets (like `debugger()`, `vcs()`, `xml()`) are **composable building blocks** designed to work together. Validating them in isolation would produce false positives because they intentionally depend on modules from other sets (e.g., `debugger()` depends on `intellij.platform.core` from `essential()`).

**Example**: 
- Product `GoLand` uses: `ide.ultimate`, `ssh`, `rd.common`
- Validation checks: Can all modules in `ide.ultimate` + `ssh` + `rd.common` resolve their dependencies within this combined set?

#### Tier 2: Self-Contained Validation

**What it validates**: Module sets marked with `selfContained = true`

**How it works**:
- Validates the module set in isolation without considering other module sets
- Ensures all dependencies are resolvable within the set itself
- Reports errors specific to that module set

**Why this tier exists**: Some module sets are designed to be **standalone/self-contained** and used directly by products without composition with other sets. These sets must have all their dependencies available internally.

**When to use `selfContained = true`**:
- Module set is used directly by products as the primary/only module set
- Module set represents a complete, independent runtime environment
- You want to enforce that the set doesn't leak dependencies on external sets

**When NOT to use `selfContained = true`**:
- Module set is designed to be composed with other sets (debugger, vcs, xml, ssh)
- Module set intentionally depends on modules from base sets like `essential()`
- Module set is a specialized feature addition to a larger base

### Example: core.platform (Self-Contained)

```kotlin
/**
 * Core platform modules without IDE or language support.
 * Used by: CodeServer (analysis tool)
 */
fun corePlatform(): ModuleSet = moduleSet(
  name = "core.platform",
  selfContained = true,  // ✅ Must be resolvable in isolation
  outputModule = "intellij.platform.ide.core"
) {
  moduleSet(librariesPlatform())
  moduleSet(rpcMinimal())  // Provides kernel + fleet deps
  
  embeddedModule("intellij.platform.core", includeDependencies = true)
  embeddedModule("intellij.platform.ide.core", includeDependencies = true)
  // ...
}
```

**Why selfContained**: CodeServer uses `core.platform` alone without other module sets. It must contain everything needed for the platform runtime.

### Example: debugger() (Composable, Not Self-Contained)

```kotlin
/**
 * Debugger platform modules.
 * Used by: All IDE products via essential()
 */
fun debugger(): ModuleSet = moduleSet(
  name = "debugger"
  // ❌ NOT selfContained - designed to compose with essential()
) {
  module("intellij.platform.debugger.impl.backend")
  embeddedModule("intellij.platform.debugger")
  // Depends on intellij.platform.core from essential() ✅ OK
}
```

**Why NOT selfContained**: Debugger is always used together with `essential()` which provides core platform modules. Validating debugger in isolation would fail because it depends on `intellij.platform.core`, but that's correct by design.

### Validation Error Examples

#### Product-Level Error

```
❌ Unresolvable dependencies in products

  Product: GoLand
    ✗ Missing: 'intellij.platform.polySymbols'
      Needed by: intellij.platform.vcs.impl
      Chain: intellij.platform.vcs.impl → intellij.platform.polySymbols
```

**What this means**: The product `GoLand` is missing a module that one of its included modules depends on. Fix by adding the missing module or a module set that contains it.

#### Self-Contained Error

```
❌ Module set 'core.platform' is marked selfContained but has unresolvable dependencies

  ✗ Missing: 'fleet.kernel'
    Needed by: intellij.platform.kernel
    Suggestion: Include fleet() or add fleet.kernel directly
```

**What this means**: A self-contained module set is missing a dependency. Fix by adding the missing module/set to make it truly self-contained.

### Troubleshooting Dependency Errors

When you encounter a dependency validation error, **resist the temptation to simply add the missing module to the failing module set**. This creates technical debt and breaks the isolation principle.

#### ❌ Wrong Approach

```kotlin
// BAD: Adding a library directly to debugger module set
fun debugger(): ModuleSet = moduleSet("debugger") {
  module("intellij.platform.debugger.impl")
  embeddedModule("intellij.libraries.kotlinx.serialization.core")  // ❌ Wrong!
}
```

**Why it's wrong**: The debugger module set should contain only debugger-related modules. Adding serialization libraries pollutes its domain and creates maintenance burden.

#### ✅ Correct Approach

1. **Identify the dependency chain**: Use PMA MCP to trace dependencies
   ```
   mcp__PluginModelAnalyzer__find_dependency_path(
     fromModule="intellij.platform.debugger.impl",
     toModule="intellij.libraries.kotlinx.serialization.core"
   )
   ```

2. **Find which module set provides the dependency**: Check existing module sets
   ```
   mcp__PluginModelAnalyzer__suggest_module_set_for_modules(
     moduleNames=["intellij.libraries.kotlinx.serialization.core"]
   )
   ```

3. **Analyze the product's module set hierarchy**: Trace what the product includes
   - Does `essentialMinimal()` → `coreLang()` → `coreIde()` → `corePlatform()` → `librariesPlatform()` include it?
   - Is there a missing link in the chain?

4. **Fix at the appropriate level**:
   - If a base module set is missing a nested set → add the nested set
   - If a product is missing a required module set → add to product
   - If the module truly belongs in the domain → then add it directly (rare)

#### Example: Serialization Library Missing

**Error**: `debugger` module set missing `intellij.libraries.kotlinx.serialization.core`

**Investigation**:
- `kotlinx.serialization.core` is in `librariesPlatform()` module set
- `librariesPlatform()` is nested in `corePlatform()`
- `corePlatform()` is nested in `coreIde()` → `coreLang()` → `essentialMinimal()`
- Product uses `essentialMinimal()` ✅ so it should have the library

**Root cause options**:
1. Product doesn't include `essentialMinimal()` - add it
2. The chain is broken somewhere - fix the nesting
3. Validation logic has a bug - investigate the validator

**Key insight**: The fix is rarely "add module X to module set Y". It's usually "ensure the module set hierarchy correctly provides module X through proper nesting".

### Best Practices

1. **Start without `selfContained`**: Most module sets should NOT be self-contained. Only flag sets that truly need isolation.

2. **Keep self-contained sets minimal**: If a set needs `selfContained = true`, try to minimize dependencies to keep it lightweight.

3. **Document why self-contained**: When using `selfContained = true`, add a KDoc comment explaining why this set needs isolation.

4. **Use product-level validation as guide**: If validation suggests adding many dependencies to a self-contained set, consider whether it should really be self-contained.

5. **Test with real products**: After changes, run "Generate Product Layouts" to validate both tiers work correctly.