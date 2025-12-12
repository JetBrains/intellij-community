# JPS to Bazel Compiler

A build tool that converts IntelliJ's JPS (JetBrains Project System) module definitions into Bazel BUILD files.

## Overview

The JPS to Bazel compiler reads `.iml` files and JPS project configuration, then generates Bazel targets that produce equivalent build outputs. This enables building IntelliJ Platform modules with Bazel while maintaining the existing JPS project structure.

### High-Level Flow

```
JPS Project Model (XML/IML files)
         │
         ▼
┌─────────────────────────────────┐
│    JpsModuleToBazel.main()      │  Entry point
│    ├─ Load JPS project          │
│    ├─ Detect community/ultimate │
│    └─ Initialize generator      │
└─────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────┐
│   BazelBuildFileGenerator       │  Core generator
│    ├─ Enumerate modules         │
│    ├─ Resolve dependencies      │
│    └─ Generate targets          │
└─────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────┐
│   Output Files                  │
│    ├─ BUILD.bazel (per module)  │
│    ├─ lib/MODULE.bazel          │
│    ├─ lib/BUILD.bazel           │
│    └─ build/bazel-targets.json  │
└─────────────────────────────────┘
```

## Source Files

| File | Lines | Purpose |
|------|-------|---------|
| `JpsModuleToBazel.kt` | ~400 | Entry point; orchestrates generation; handles workspace detection and two-pass execution |
| `BazelBuildFileGenerator.kt` | ~1200 | Core generator; processes modules, computes dependencies, generates jvm_library targets |
| `dependency.kt` | ~680 | Dependency analysis; converts JPS dependencies to Bazel labels; handles scope mapping |
| `lib.kt` | ~350 | Library handling; generates jvm_import/java_import targets for Maven and local libraries |
| `dsl.kt` | ~160 | Bazel DSL model; BuildFile/Target/LoadStatement classes for code generation |
| `BazelFileUpdater.kt` | ~70 | Incremental file updates; preserves manual edits via section markers |
| `ModuleDescriptor.kt` | ~60 | Data class representing a parsed JPS module with all its metadata |
| `UrlCache.kt` | ~310 | HTTP/Maven resolution; caches JAR URLs and SHA256 checksums |
| `CompareJpsWithBazel.kt` | ~180 | Verification tool; compares JPS vs Bazel compilation output |
| `BazelProjectStructure.kt` | ~50 | Utility for traversing BUILD file structure |
| `PrepareLogForJaeger.kt` | ~40 | Tracing/profiling utilities |

## Data Models

### ModuleDescriptor

Represents a parsed JPS module:

```kotlin
ModuleDescriptor(
  imlFile: Path,                         // Location of .iml file
  module: JpsModule,                     // JPS model object
  contentRoots: List<Path>,              // Module content directories
  sources: List<SourceDirDescriptor>,    // Production source roots
  resources: List<ResourceDescriptor>,   // Production resources
  testSources: List<SourceDirDescriptor>,// Test source roots
  testResources: List<ResourceDescriptor>,// Test resources
  isCommunity: Boolean,                  // In community/ or ultimate/
  bazelBuildFileDir: Path,               // Where to write BUILD.bazel
  targetName: String                     // Bazel target name
)
```

### ModuleList

Container grouping all modules:

```kotlin
ModuleList(
  community: List<ModuleDescriptor>,     // Community modules
  ultimate: List<ModuleDescriptor>,      // Ultimate modules
  skippedModules: List<String>,          // Modules not converted
  deps: Map<ModuleDescriptor, ModuleDeps>,    // Production dependencies
  testDeps: Map<ModuleDescriptor, ModuleDeps> // Test dependencies
)
```

### ModuleDeps

Resolved dependencies for a module:

```kotlin
ModuleDeps(
  deps: List<BazelLabel>,        // Regular compile dependencies
  provided: List<BazelLabel>,    // Provided scope (compile-only, neverlink)
  runtimeDeps: List<BazelLabel>, // Runtime-only dependencies
  exports: List<BazelLabel>,     // Re-exported dependencies
  associates: List<BazelLabel>,  // Test friend modules
  plugins: List<String>          // Kotlin compiler plugins
)
```

### Library Types

```kotlin
sealed interface Library

MavenLibrary(
  mavenCoordinates: String,              // "group:artifact:version"
  jars: List<MavenFileDescription>,      // Compiled JARs
  sourceJars: List<MavenFileDescription>,
  target: LibraryTarget
)

LocalLibrary(
  files: List<Path>,                     // Direct file references
  bazelBuildFileDir: Path,
  target: LibraryTarget
)
```

## Generation Pipeline

### Phase 1: Project Loading

```kotlin
// JpsModuleToBazel.kt
JpsSerializationManager.getInstance().loadProject(
  projectPath = communityRoot,
  pathVariables = mapOf("MAVEN_REPOSITORY" to m2Repo),
  loadUnloadedModules = true
)
```

Loads the complete JPS model including:
- All `.iml` module files
- `.idea/libraries/*.xml` library definitions
- `.idea/jarRepositories.xml` for Maven repository URLs
- Kotlin facet metadata

### Phase 2: Module Enumeration

For each JPS module:

1. **Locate BUILD.bazel directory**: Walk up from `.iml` directory until finding a parent containing all content roots
2. **Determine community/ultimate**: Check if path starts with `communityRoot`
3. **Compute source roots**: Create glob patterns (`**/*.kt`, `**/*.java`, `**/*.form`)
4. **Compute resource roots**: Extract base directory and output path mappings
5. **Generate target name**: Convert module name (e.g., `intellij.platform.core` → `core`)

### Phase 3: Dependency Analysis

For each module dependency:

```kotlin
// dependency.kt - generateDeps()
when (element) {
  is JpsModuleDependency -> {
    // Resolve to ModuleDescriptor
    // Get scope: COMPILE, PROVIDED, RUNTIME, TEST
    // Create BazelLabel
  }
  is JpsLibraryDependency -> {
    // Check if Maven or local library
    // Extract coordinates or file paths
    // Generate library target
  }
}
```

### Phase 4: Two-Pass Generation

The generator processes modules in two passes:

1. **Community Pass**: Generate BUILD files for all community modules, collect used libraries
2. **Ultimate Pass**: Generate BUILD files for ultimate modules, handle shared library references

This separation ensures community libraries are defined in `@lib//` and can be reused by ultimate modules.

### Phase 5: Build Target Generation

For each module:

```python
# Production target
jvm_library(
    name = "platform-core",
    srcs = glob(["src/**/*.kt", "src/**/*.java"]),
    resources = [":platform-core_resources"],
    deps = [":util", "@lib//:annotations"],
    exports = [":exported-dep"],
    visibility = ["//visibility:public"],
)

# Test target (if test sources exist)
jvm_library(
    name = "platform-core_test_lib",
    srcs = glob(["test/**/*.kt", "test/**/*.java"]),
    associates = [":platform-core"],  # Test friend access
    deps = [...],
)

jps_test(
    name = "platform-core_test",
    runtime_deps = [":platform-core_test_lib"],
)

# Resource target
resourcegroup(
    name = "platform-core_resources",
    srcs = glob(["resources/**/*"]),
    strip_prefix = "resources",
)
```

### Phase 6: Library Generation

**Maven Libraries:**
```python
# lib/MODULE.bazel
http_file(
    name = "maven_org_jetbrains_annotations__file",
    url = "https://repo1.maven.org/.../annotations-24.0.0.jar",
    sha256 = "abc123...",
)

# lib/BUILD.bazel
jvm_import(
    name = "annotations",
    jar = "@maven_org_jetbrains_annotations__file//file",
    source_jar = "@maven_org_jetbrains_annotations_sources__file//file",
)
```

**Local Libraries:**
```python
java_import(
    name = "local-lib",
    jars = ["snapshots/lib-1.0-abc123.jar"],
)
```

### Phase 7: Output & Cleanup

1. Save all BUILD.bazel files atomically (temp file + move)
2. Verify HTTP file checksums match MODULE.bazel
3. Generate `build/bazel-targets.json` manifest
4. Delete old generated files not in current run
5. Save `build/bazel-generated-file-list.txt` for tracking

## Dependency Transformation

### Scope Mapping

| JPS Scope | Bazel Attribute | Notes |
|-----------|-----------------|-------|
| COMPILE | `deps` | Regular compile+runtime dependency |
| PROVIDED | `provided` → `jvm_provided_library` | Compile-only, `neverlink=true` |
| RUNTIME | `runtime_deps` | Runtime-only, not on compile classpath |
| TEST | Test-specific | Uses `associates` for test friend access |

### Cross-Module References

| From | To | Label Format |
|------|----|--------------|
| Community | Community | `//path:target` |
| Ultimate | Community | `@community//path:target` |
| Ultimate | Ultimate | `//path:target` |
| Community | Ultimate | **Not allowed** (enforced) |

### Test Friend Modules

Test modules get special `associates` attribute for internal access:

```python
jvm_library(
    name = "platform-core_test_lib",
    associates = [":platform-core"],  # Can access internal members
)
```

## Library Handling

### Maven Libraries

1. Extract Maven coordinates from m2 path: `group:artifact:version`
2. Resolve JAR URL from configured repositories (`.idea/jarRepositories.xml`)
3. Verify SHA256 checksum from JPS library metadata
4. Generate `http_file` rule in `lib/MODULE.bazel`
5. Generate `jvm_import` target in `lib/BUILD.bazel`

### Snapshot Libraries

Libraries with `-SNAPSHOT` versions or outside the project tree:

1. Detect snapshot version from path or version string
2. Copy JAR to `lib/snapshots/` with content hash in filename
3. Generate local `java_import` target

### Local Libraries

Libraries tracked in VCS under `lib/` directory:

1. Verify files are under same BUILD.bazel directory
2. Generate `java_import` target with relative paths

## Configuration

### Command-Line Arguments

```bash
--workspace_directory=<path>           # Override workspace root
--run_without_ultimate_root=true       # Community-only mode
--default-custom-modules=true          # Include predefined custom modules
--m2-repo=<path>                       # Maven repository location
--assert-all-outputs-exist-with-output-base=<path>  # Verify output JARs
```

### Environment Variables

| Variable | Description |
|----------|-------------|
| `BUILD_WORKSPACE_DIRECTORY` | Override workspace detection |
| `RUN_WITHOUT_ULTIMATE_ROOT` | Force community-only mode |
| `JPS_TO_BAZEL_TREAT_KOTLIN_DEV_VERSION_AS_SNAPSHOT` | Treat Kotlin dev versions as snapshots |
| `MAVEN_REPOSITORY` | Maven local repository path |

### Root Detection

The generator searches for marker files to find project roots:

- `.community.root.marker` → Community root
- `.ultimate.root.marker` → Ultimate root (in parent of community)

## Output Files

| File | Description |
|------|-------------|
| `BUILD.bazel` | Per-module target definitions (next to module content) |
| `lib/MODULE.bazel` | `http_file` rules for Maven dependencies with SHA256 |
| `lib/BUILD.bazel` | `jvm_import` and `java_import` library targets |
| `build/bazel-targets.json` | Module→JAR mapping for IDE integration |
| `build/bazel-generated-file-list.txt` | List of generated files for cleanup |

### bazel-targets.json Structure

```json
{
  "modules": {
    "intellij.platform.core": {
      "productionTargets": ["@community//platform/core:core"],
      "productionJars": ["bazel-out/.../core.jar"],
      "testTargets": ["@community//platform/core:core_test_lib"],
      "testJars": ["bazel-out/.../core_test_lib.jar"],
      "exports": ["@lib//:annotations"],
      "moduleLibraries": {}
    }
  },
  "projectLibraries": {
    "annotations": {
      "target": "@lib//:annotations",
      "jars": ["external/..."],
      "sourceJars": ["external/..."]
    }
  }
}
```

## Auto-Generated Section Management

`BazelFileUpdater` manages incremental updates while preserving manual edits:

### Section Markers

```python
# Manual code here is preserved

### auto-generated section `build modulename` start
jvm_library(
    name = "modulename",
    ...
)
### auto-generated section `build modulename` end

# More manual code preserved
```

### Skip Directive

To disable generation for a specific section:

```python
### skip generation section `build modulename`
# Your custom target here
```

## Adding New Features

### Adding a New Target Type

1. **Define target generation** in `BazelBuildFileGenerator.kt`:
   ```kotlin
   private fun generateCustomTarget(module: ModuleDescriptor): Target {
     return target("custom_rule") {
       option("name", module.targetName)
       // Add options
     }
   }
   ```

2. **Add load statement** in the build file:
   ```kotlin
   buildFile.load("@rules//:defs.bzl", "custom_rule")
   ```

3. **Call from appropriate generation phase** (production or test targets)

### Supporting a New Dependency Scope

1. **Extend scope handling** in `dependency.kt`:
   ```kotlin
   // In addDep() function
   JpsJavaDependencyScope.NEW_SCOPE -> {
     moduleDeps.newScopeDeps.add(label)
   }
   ```

2. **Update ModuleDeps** data class with new list

3. **Generate attribute** in target:
   ```kotlin
   option("new_scope_deps", moduleDeps.newScopeDeps)
   ```

### Adding Compiler Plugin Support

1. **Detect plugin** in `BazelBuildFileGenerator.computeKotlincOptions()`:
   ```kotlin
   val pluginJar = pluginClasspath.find { it.name.startsWith("my-plugin-") }
   if (pluginJar != null) {
     moduleDeps.plugins.add("@lib//:my-plugin")
   }
   ```

2. **Define library target** for the plugin in `lib/BUILD.bazel`

### Extending Library Handling

1. **Add new library type** in `lib.kt`:
   ```kotlin
   class CustomLibrary(
     val customData: String,
     override val target: LibraryTarget
   ) : Library
   ```

2. **Add detection logic** in `dependency.kt`:
   ```kotlin
   private fun getLibrary(library: JpsLibrary): Library? {
     if (isCustomLibrary(library)) {
       return CustomLibrary(...)
     }
     // existing logic
   }
   ```

3. **Generate targets** in `lib.kt`:
   ```kotlin
   fun generateCustomLib(lib: CustomLibrary): Target { ... }
   ```

## Testing

### Integration Tests

Located in `test/org/jetbrains/intellij/build/bazel/BazelGeneratorIntegrationTests.kt`

Test cases use projects in `testData/integration/`:
- `kotlin-snapshot-library`
- `snapshot-repository-library`
- `snapshot-library`
- `snapshot-library-in-tree`

### Running Tests

```bash
bazel test //:jps-to-bazel-tests
```

### Verification Tool

`CompareJpsWithBazel.kt` compares JPS and Bazel compilation outputs:
- Validates JAR file counts match
- Compares file contents
- Verifies resource structure

## Kotlin & Java Compiler Options

### Kotlin Options

Extracted from `JpsKotlinFacetModuleExtension`:

- `api_version`, `language_version`
- `opt_in` annotations
- `plugin_options` for compiler plugins
- Various `-X` flags

Generates `kt_kotlinc_options` target when options are present.

### Java Options

From `JpsJavaExtensionService.projectCompilerConfiguration`:

- `--add-exports` module directives
- Annotation processing settings

Generates `kt_javac_options` target.

## Special Handling

### Custom Modules

Predefined in `DEFAULT_CUSTOM_MODULES`:

| JPS Module | Bazel Label |
|------------|-------------|
| `intellij.idea.community.build.zip` | `@community//build:zip` |
| `intellij.platform.jps.build.dependencyGraph` | `@community//build:dependency-graph` |
| `intellij.platform.jps.build.javac.rt` | `@community//build:build-javac-rt` |

### Skipped Modules

- `intellij.platform.buildScripts.bazel` (the generator itself)
- `intellij.tools.build.bazel.jvmIncBuilder`
- `intellij.tools.build.bazel.jvmIncBuilderTests`

### Provided Dependencies

When a library is marked `PROVIDED` scope:

1. Track in `ProvidedLibraries` multimap
2. Generate `jvm_provided_library` wrapper with `neverlink=true`
3. Reference wrapper from dependent modules
