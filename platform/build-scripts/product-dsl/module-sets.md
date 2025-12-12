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

See [Validation Documentation](validation.md) for comprehensive coverage of:
- Two-tier validation system (product-level and self-contained)
- Cross-plugin dependency validation
- Loading attribute semantics (`embedded`/`required` vs `optional`/`on_demand`)
- `allowMissingDependencies` usage
- Troubleshooting validation errors