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

| Location | File Path | Description |
|----------|-----------|-------------|
| Community | `community/platform/build-scripts/src/org/jetbrains/intellij/build/productLayout/CommunityModuleSets.kt` | IDE feature sets (essential, vcs, xml, debugger) |
| Community (Core) | `community/platform/build-scripts/src/org/jetbrains/intellij/build/productLayout/CoreModuleSets.kt` | Platform infrastructure (libraries, corePlatform, rpc) |
| Ultimate | `platform/buildScripts/src/productLayout/UltimateModuleSets.kt` | Ultimate-only module sets |

Each module set is defined as a Kotlin function (e.g., `fun essential(): ModuleSet`) that returns a `ModuleSet` object. The XML files (pattern: `intellij.moduleSets.<name>.xml`) are **auto-generated** from this Kotlin code.

### Discovery Mechanism

Module sets are discovered automatically via **reflection**. The generator scans provider objects (like `CommunityModuleSets`) for all public no-argument functions that return `ModuleSet`:

```kotlin
// From discovery/ModuleSetDiscovery.kt
fun discoverModuleSets(provider: Any): List<ModuleSet> {
  // Finds all public functions returning ModuleSet with no parameters
  // Caches method handles for performance
}
```

**This means**: Any public function returning `ModuleSet` in a provider object will be automatically discovered and have its XML generated.

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

## DSL Function Reference

The `moduleSet()` DSL provides these functions for building module sets. For the complete DSL reference including all loading modes and product-level functions, see [dsl-api-reference.md](dsl-api-reference.md).

### `moduleSet()` - Create a Module Set

```kotlin
fun moduleSet(
  name: String,                      // Required: identifier for the set
  alias: String? = null,             // Optional: module alias (e.g., "com.intellij.modules.xml")
  outputModule: String? = null,      // Optional: module whose resources dir receives generated XML
  selfContained: Boolean = false,    // Optional: validate in isolation
  includeDependencies: Boolean = false, // Optional: default for embedded modules
  block: ModuleSetBuilder.() -> Unit
): ModuleSet
```

### `module()` - Add a Regular Module

```kotlin
fun module(name: String, loading: ModuleLoadingRuleValue? = null)
```

Adds a module with default or specified loading mode. Most modules use this.

### `embeddedModule()` - Add an Embedded Module

```kotlin
fun embeddedModule(name: String)
```

Adds a module with `loading="embedded"`. Use for modules that must load in the core classloader:
- Modules with compile dependencies from other embedded modules
- Core platform infrastructure modules
- Modules needed very early in IDE startup

### `requiredModule()` - Add a Required Module

```kotlin
fun requiredModule(name: String)
```

Adds a module with `loading="required"`. Use for test framework modules and other modules that must be present but don't need embedded loading.

### `moduleSet()` - Include a Nested Module Set

```kotlin
fun moduleSet(set: ModuleSet)
```

Includes another module set. Creates hierarchical composition:

```kotlin
fun ideCommon() = moduleSet("ide.common") {
  moduleSet(essential())  // Nest essential modules
  moduleSet(vcs())        // Nest VCS modules
  moduleSet(xml())        // Nest XML modules
}
```

## Parameters Reference

### `alias` - Module Alias

Generates a `<module value="..."/>` declaration in the XML, allowing plugins to depend on this module set as a module:

```kotlin
fun xml() = moduleSet("xml", alias = "com.intellij.modules.xml") {
  // ...
}
```

Generated XML includes:
```xml
<module value="com.intellij.modules.xml"/>
```

### `outputModule` - Custom Output Location

Specifies which module's resources directory should contain the generated XML:

```kotlin
fun corePlatform() = moduleSet("core.platform", outputModule = "intellij.platform.ide.core") {
  // Generated XML goes to intellij.platform.ide.core's META-INF/ instead of default location
}
```

Use when the module set needs to be in a specific location for classloader or packaging reasons.

### `selfContained` - Isolated Validation

When `true`, the module set is validated in isolation to ensure all dependencies are resolvable within the set itself:

```kotlin
fun corePlatform() = moduleSet("core.platform", selfContained = true) {
  // Must include ALL dependencies - no external module references allowed
}
```

Use for module sets designed to be standalone building blocks.

### `includeDependencies` - Auto-Include Dependencies

When `true`, embedded modules in this set automatically include their implementation-only transitive dependencies:

```kotlin
fun essential() = moduleSet("essential", includeDependencies = true) {
  embeddedModule("intellij.platform.core")  // Inherits includeDependencies=true
}
```

See [programmatic-content.md](programmatic-content.md) for details on content vs implementation modules.

## Real-World Examples

### Simple Feature Module Set

```kotlin
/**
 * VCS frontend modules.
 */
fun vcsFrontend(): ModuleSet = moduleSet("vcs.frontend") {
  module("intellij.platform.vcs.impl.frontend")
}
```

### Module Set with Nested Sets

```kotlin
/**
 * VCS (Version Control System) modules including shared and frontend parts.
 */
fun vcs(): ModuleSet = moduleSet("vcs") {
  module("intellij.platform.vcs.impl")
  module("intellij.platform.vcs.impl.exec")
  module("intellij.platform.vcs.log")
  module("intellij.platform.vcs.log.impl")
  embeddedModule("intellij.platform.vcs")

  moduleSet(vcsShared())    // Nest shared modules
  moduleSet(vcsFrontend())  // Nest frontend modules
}
```

### Module Set with Alias

```kotlin
/**
 * XML support modules.
 */
fun xml(): ModuleSet = moduleSet("xml", alias = "com.intellij.modules.xml") {
  embeddedModule("intellij.xml.dom")
  embeddedModule("intellij.xml.psi")
  embeddedModule("intellij.xml.psi.impl")
  module("intellij.xml.emmet")
  module("intellij.relaxng")
  // ...
}
```

### Test Framework Module Set (using `requiredModule`)

```kotlin
/**
 * Test framework libraries (JUnit 4, JUnit 5, Hamcrest).
 * Use when a product (or test plugin) must bundle these explicitly; avoid adding it to a DSL test
 * plugin when the product already provides the same modules (auto-add will skip resolvable deps).
 */
fun librariesTestFrameworks(): ModuleSet = moduleSet("libraries.testFrameworks") {
  requiredModule("intellij.libraries.assertj.core")
  requiredModule("intellij.libraries.hamcrest")
  requiredModule("intellij.libraries.junit4")
  requiredModule("intellij.libraries.junit5")
  requiredModule("intellij.libraries.junit5.jupiter")
}
```

### Large Composite Module Set

```kotlin
/**
 * Essential platform modules required by most IDE products.
 */
fun essential(): ModuleSet = moduleSet("essential", includeDependencies = true) {
  // Include minimal essential modules
  moduleSet(essentialMinimal())
  moduleSet(debugger())

  // Embedded modules (core classloader)
  embeddedModule("intellij.platform.scopes")
  embeddedModule("intellij.platform.find")
  embeddedModule("intellij.platform.polySymbols")

  // Regular modules
  module("intellij.platform.navbar")
  module("intellij.platform.navbar.backend")
  module("intellij.platform.clouds")
  module("intellij.platform.todo")
  // ... many more
}
```

## Creating a New Module Set

See `/create-module-set` slash command for detailed instructions on creating a new module set.

**Quick checklist:**
1. Add function to appropriate file (`CommunityModuleSets.kt` or `UltimateModuleSets.kt`)
2. Write comprehensive KDoc (see existing examples)
3. Run "Generate Product Layouts" to create XML
4. Reference from products via `moduleSet(yourSet())`

## Discovering Available Module Sets

To find available module sets and understand their contents:

1. **Browse the source code** - Open the Kotlin files listed in [Module Set Locations](#module-set-locations)
   
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
- **Use required loading**: `requiredModule("intellij.libraries.junit5")` for test frameworks
- **Include dependencies**: `includeDependencies = true` to automatically pull in module dependencies

**Tip**: Prefer nesting existing module sets over duplicating modules. This creates a clean hierarchy and ensures consistency.

## Troubleshooting

### Module set not appearing in generated XML

**Cause**: Function is not public or doesn't return `ModuleSet` directly.

**Fix**: Ensure your function is:
```kotlin
fun mySet(): ModuleSet = moduleSet("my.set") { ... }  // ✓ Correct
private fun mySet() = ...  // ✗ Not discovered (private)
fun mySet(): Any = ...     // ✗ Not discovered (wrong return type)
```

### "No resource root found for module" error

**Cause**: `outputModule` specifies a module without a resources directory.

**Fix**: Either:
- Add a resources root to the specified module
- Remove `outputModule` to use default location
- Specify a different module with resources

### Circular module set reference

**Cause**: Module set A includes B, and B includes A.

**Fix**: Restructure to break the cycle. Usually extract common modules to a third set.

### Orphaned XML file warning

**Cause**: A module set function was renamed/removed but its XML file remains.

**Fix**: The generator automatically cleans orphaned files. If persisting, manually delete the `.xml` file.

## Module Set Validation

See [validation-rules.md](validation-rules.md) for validation rules and [errors.md](errors.md) for troubleshooting.