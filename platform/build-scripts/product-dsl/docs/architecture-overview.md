# Architecture Overview

High-level architecture of the product-dsl module and how it fits into the IntelliJ build system.

## Purpose

The product-dsl module provides a **Kotlin DSL for defining product module composition**. It replaces manual XML editing with type-safe, composable Kotlin code that:

- Defines module sets (reusable module collections)
- Specifies product content (which modules go into each IDE product)
- Generates XML files for the runtime module system
- Validates dependencies at build time

## System Context

```
┌─────────────────────────────────────────────────────────────────────┐
│                        IntelliJ Build System                         │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────────┐     ┌──────────────┐     ┌──────────────────────┐ │
│  │  JPS Modules │     │ product-dsl  │     │  Generated XML       │ │
│  │  (*.iml)     │────▶│  (Kotlin)    │────▶│  (plugin.xml,        │ │
│  │              │     │              │     │   moduleSets/*.xml)  │ │
│  └──────────────┘     └──────────────┘     └──────────────────────┘ │
│         │                    │                       │               │
│         │                    │                       ▼               │
│         │                    │              ┌──────────────────────┐ │
│         │                    │              │  IDE Runtime         │ │
│         │                    │              │  (module loading)    │ │
│         │                    │              └──────────────────────┘ │
│         │                    │                                       │
│         │                    ▼                                       │
│         │           ┌──────────────────┐                            │
│         └──────────▶│  Validation      │                            │
│                     │  (dependencies)  │                            │
│                     └──────────────────┘                            │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

## Data Flow

### 1. Input: Kotlin Definitions

```
CommunityModuleSets.kt   ──┐
CoreModuleSets.kt        ──┼──▶  Module Set Definitions
UltimateModuleSets.kt    ──┘

*Properties.kt files     ──────▶  Product Content Specs
```

### 2. Processing: Generation Pipeline

The generation uses a **5-stage pipeline architecture** with pluggable generators:

```
┌─────────────────────────────────────────────────────────────────┐
│             GenerationPipeline (pipeline/GenerationPipeline.kt)  │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  STAGE 1: DISCOVER (DiscoveryStage)                              │
│     ├─ discoverModuleSets() - Find all module set functions     │
│     └─ discoverAllProducts() - Find all product definitions     │
│     → DiscoveryResult { moduleSetsByLabel, products }           │
│                                                                  │
│  STAGE 2: BUILD_MODEL (ModelBuildingStage)                       │
│     ├─ Build PluginGraph (single source of truth for products,  │
│     │  module sets, plugins)                                    │
│     ├─ Collect product aliases + content from the graph         │
│     ├─ Create ModuleDescriptorCache (async)                     │
│     ├─ Pre-warm PluginContentCache                              │
│     └─ Compute shared: embeddedModules, allPluginModules        │
│     → GenerationModel { discovery, config, caches, shared }     │
│                                                                  │
│  STAGE 3: GENERATE (Parallel ComputeNodes)                       │
│     Nodes execute in topological order based on slot deps:      │
│     ├─ ModuleSetXmlNode             - Module set XML files      │
│     ├─ ProductModuleDependencyNode  - Module descriptors        │
│     ├─ ContentModuleDependencyNode  - moduleName.xml deps       │
│     ├─ TestDescriptorNode           - moduleName._test.xml      │
│     ├─ PluginXmlDependencyNode      - plugin.xml deps           │
│     ├─ PluginValidationNode         - validates plugin deps     │
│     ├─ SuppressionConfigNode        - suppressions.json         │
│     ├─ ProductXmlNode               - Product plugin.xml        │
│     └─ TestPluginXmlNode            - Test plugin XML + auto-add│
│     → Data flows through typed DataSlot<T> channels             │
│                                                                  │
│  STAGE 4: AGGREGATE                                              │
│     ├─ Collect errors from all generators                       │
│     ├─ Collect diffs from file updater                          │
│     └─ Merge tracking maps for orphan cleanup                   │
│     → AggregatedResult { errors, diffs, trackingMaps }          │
│                                                                  │
│  STAGE 5: OUTPUT                                                 │
│     ├─ Cleanup orphaned module set files                        │
│     ├─ Commit deferred writes (if commitChanges=true)           │
│     └─ Build GenerationStats from generator results             │
│     → GenerationResult { errors, diffs, stats }                 │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

**ComputeNode architecture:** Each node implements the `ComputeNode` interface and declares data dependencies via `requires` (input slots) and `produces` (output slots). The pipeline infers execution order from slot dependencies and runs independent nodes in parallel.

### 3. Output: Generated Files

| Output Type | Location | Purpose |
|-------------|----------|---------|
| Module Set XML | `*/generated/META-INF/intellij.moduleSets.*.xml` | Module set definitions for xi:include |
| Module Descriptor XML | `<module>/resources/META-INF/<module>.xml` | Module dependencies |
| Test Plugin XML | `<module>/testResources/META-INF/plugin.xml` | Test plugin definitions |

## Key Components

### Module Sets Layer

```
┌─────────────────────────────────────────────────┐
│              Module Sets                         │
├─────────────────────────────────────────────────┤
│  CoreModuleSets                                  │
│  ├─ libraries() - Library wrapper modules       │
│  ├─ corePlatform() - Core platform modules      │
│  ├─ coreLang() - Language support               │
│  └─ rpc*() - RPC infrastructure                 │
│                                                  │
│  CommunityModuleSets                             │
│  ├─ essential() - Essential IDE modules         │
│  ├─ vcs() - Version control                     │
│  ├─ xml() - XML support                         │
│  ├─ debugger() - Debugger platform              │
│  └─ ideCommon() - Common IDE modules            │
│                                                  │
│  UltimateModuleSets                              │
│  ├─ ideUltimate() - Ultimate IDE base           │
│  ├─ commercialIdeBase() - Commercial products   │
│  └─ ssh() - SSH support                         │
└─────────────────────────────────────────────────┘
```

### Product Layer

```
┌─────────────────────────────────────────────────┐
│              Products                            │
├─────────────────────────────────────────────────┤
│  Each product defines:                           │
│  ├─ getProductContentDescriptor() → spec        │
│  │   ├─ alias() - Product module aliases        │
│  │   ├─ moduleSet() - Include module sets       │
│  │   ├─ module() - Individual modules           │
│  │   ├─ deprecatedInclude() - XML includes      │
│  │   ├─ bundledPlugins() - Bundled plugin list  │
│  │   └─ testPlugin() - Test plugin definitions  │
│  │                                               │
│  Examples:                                       │
│  ├─ IntelliJIdeaUltimateProperties              │
│  ├─ GoLandProperties                             │
│  ├─ CLionProperties                              │
│  └─ PyCharmProperties                            │
└─────────────────────────────────────────────────┘
```

### Validation Layer

```
┌─────────────────────────────────────────────────┐
│              Validation                          │
├─────────────────────────────────────────────────┤
│  Tier 1: Structural (DSL Constraints)            │
│  ├─ No duplicate modules across sets             │
│  ├─ Valid loading overrides                      │
│  ├─ Unique module aliases                        │
│  └─ No redundant module set references           │
│                                                  │
│  Tier 2: Module Dependencies                     │
│  ├─ Self-contained sets have all deps            │
│  ├─ Library modules used (not direct libs)       │
│  └─ Test libraries in TEST scope                 │
│                                                  │
│  Tier 3: Plugin Dependencies                     │
│  ├─ Structural: EMBEDDED→OPTIONAL violations     │
│  ├─ Availability: Deps resolve in products       │
│  └─ Content module plugin deps match IML→XML     │
└─────────────────────────────────────────────────┘
```

## Entry Points

For running the generator and common commands, see [quick-start.md](quick-start.md#run-commands).

- **IDE:** Run configuration `Generate Product Layouts`
- **Bazel:** `bazel run //platform/buildScripts:plugin-model-tool`
- **JSON Analysis:** See [programmatic-content.md#json-analysis-endpoint](programmatic-content.md#json-analysis-endpoint)

## File Organization

```
community/platform/build-scripts/product-dsl/
├── docs/                          # Documentation
│   ├── architecture-overview.md   # This file
│   ├── dsl-api-reference.md       # DSL function reference
│   ├── module-sets.md             # Module sets guide
│   ├── programmatic-content.md    # Content spec guide
│   ├── validation-rules.md        # Validation rules
│   ├── errors.md                  # Error reference
│   ├── test-plugins.md            # Test plugin guide
│   └── ...
│
├── src/                           # Source code
│   ├── ProductModulesContentSpec.kt  # Main DSL types
│   ├── ModuleSetBuilder.kt           # Module set DSL
│   ├── ContentBlockBuilder.kt        # Content traversal
│   │
│   ├── pipeline/                  # Pipeline architecture
│   │   ├── GenerationPipeline.kt     # Main orchestrator
│   │   ├── ComputeNode.kt            # ComputeNode interface & context
│   │   ├── Slots.kt                  # Typed data slots
│   │   ├── Generator.kt              # GeneratorId/GeneratorIds only
│   │   ├── PipelineTypes.kt          # Stage result types
│   │   ├── stages/                   # Pipeline stages
│   │   │   ├── DiscoveryStage.kt
│   │   │   └── ModelBuildingStage.kt
│   │   └── generators/               # ComputeNode implementations
│   │       ├── ModuleSetXmlGenerator.kt         # ModuleSetXmlNode
│   │       ├── ProductModuleDependencyGenerator.kt  # ProductModuleDependencyNode
│   │       ├── ContentModuleDependencyGenerator.kt  # ContentModuleDependencyPlanNode
│   │       ├── ContentModuleXmlWriter.kt        # ContentModuleXmlWriteNode
│   │       ├── TestDescriptorGenerator.kt       # TestDescriptorNode
│   │       ├── PluginXmlDependencyGenerator.kt  # PluginDependencyPlanNode
│   │       ├── PluginXmlWriter.kt               # PluginXmlWriteNode
│   │       ├── PluginValidationGenerator.kt     # PluginValidationNode
│   │       ├── SuppressionConfigGenerator.kt    # SuppressionConfigNode
│   │       ├── ProductXmlGenerator.kt           # ProductXmlNode
│   │       ├── TestPluginDependencyPlanner.kt   # TestPluginDependencyPlanNode
│   │       └── TestPluginXmlGenerator.kt        # TestPluginXmlNode
│   │
│   ├── discovery/                 # Discovery & product handling
│   │   ├── ModuleSetDiscovery.kt
│   │   ├── ProductGeneration.kt      # Entry point (delegates to pipeline)
│   │   └── PluginContentExtractor.kt
│   │
│   ├── validation/                # Validation layer
│   │   ├── ValidationModels.kt
│   │   ├── ValidationFormatters.kt
│   │   └── rules/
│   │       ├── DslConstraints.kt
│   │       ├── PluginDependencyValidation.kt
│   │       ├── PluginDependencyResolution.kt
│   │       ├── ContentModulePluginDepValidation.kt
│   │       └── StructuralViolationFix.kt
│   │
│   └── dependency/                # Dependency extraction helpers
│       ├── ModuleDescriptorCache.kt
│       └── PluginContentCache.kt
│
└── testSrc/                       # Tests
    └── pipeline/                  # Pipeline tests
        └── GenerationPipelineTest.kt
```

## Integration Points

### With JPS (Module System)

- Reads module dependencies from `*.iml` files
- JPS is source of truth for module structure
- product-dsl generates content based on JPS dependencies

### With Bazel

- Bazel BUILD files are auto-generated from `*.iml`
- product-dsl runs in Bazel context for CI validation
- Both systems must stay synchronized

### With IDE Runtime

- Generated XML files loaded by plugin system at startup
- Module loading order determined by loading attributes
- Dependencies declared in module descriptor XML

## See Also

- [quick-start.md](quick-start.md) - Getting started guide
- [dsl-api-reference.md](dsl-api-reference.md) - Complete DSL reference
- [validation-rules.md](validation-rules.md) - Validation architecture
