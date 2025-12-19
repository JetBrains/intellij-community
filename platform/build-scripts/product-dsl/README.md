# Product DSL

A Kotlin DSL for defining IntelliJ product module composition and generating module dependencies.

## How to Run

- **IDE:** Run configuration `Generate Product Layouts`
- **Bazel:** `bazel run //platform/buildScripts:plugin-model-tool`
- **CLI flags:** `--json` outputs model analysis; `--json='{"filter":"products"}'` for specific sections

## Architecture

```
src/
├── [Core DSL]
│   ├── GeneratorModel.kt            # Core types: ModuleSet, Module, ModuleLoadingRule
│   ├── ProductModulesContentSpec.kt # Product specification DSL
│   ├── ModuleSetBuilder.kt          # DSL builder for module sets
│   └── generator.kt                 # Main entry points
│
├── validation/                      # Validation framework
│   ├── ValidationModels.kt          # Error types (DependencyError, LocationError, etc.)
│   ├── ValidationFormatters.kt      # Error formatting for tests/output
│   └── rules/                       # Validation rules
│       ├── DependencyValidation.kt      # Module dependency validation
│       ├── LibraryModuleValidation.kt   # Library module dependency checks
│       ├── LocationValidation.kt        # Community/ultimate location checks
│       └── DslConstraints.kt            # DSL constraint validation
│
├── traversal/                       # Module set traversal utilities
│   ├── ModuleSetTraversal.kt        # Graph traversal algorithms
│   ├── ModuleSetTraversalCache.kt   # O(1) cached lookups
│   ├── ModulePathAnalysis.kt        # Module-to-product path tracing
│   └── ModuleDependencyAnalysis.kt  # JPS dependency analysis
│
├── tooling/                         # MCP server / analysis tools
│   ├── AnalysisModels.kt            # Data models for analysis results
│   ├── SimilarityAnalysis.kt        # Product/module set similarity
│   ├── UnificationAnalysis.kt       # Merge/refactor suggestions
│   └── DuplicateIncludeDetector.kt  # xi:include duplicate detection
│
├── dependency/                      # Dependency generation
│   ├── ModuleDescriptorDependencyGenerator.kt  # Product module deps
│   ├── PluginDependencyGenerator.kt            # Plugin content module deps
│   └── ModuleDescriptorCache.kt                # Async descriptor caching
│
├── discovery/                       # Product/module discovery
│   ├── ProductDiscovery.kt          # dev-build.json parsing
│   ├── ProductGeneration.kt         # Main orchestration
│   ├── ModuleSetDiscovery.kt        # Reflection-based module set discovery
│   └── PluginContentExtractor.kt    # xi:include resolution
│
├── json/                            # JSON output for MCP server
├── xml/                             # XML generation utilities
├── stats/                           # Generation statistics
└── util/                            # Shared utilities
```

## Package Overview

| Package | Purpose |
|---------|---------|
| **validation/** | Framework for validating module sets and products |
| **validation/rules/** | Individual validation rules (dependency, location, DSL constraints) |
| **traversal/** | Module set graph traversal with caching for O(1) lookups |
| **tooling/** | Analysis tools for MCP server (similarity, unification suggestions) |
| **dependency/** | Generates `<dependencies>` sections in module descriptors |
| **discovery/** | Discovers products and module sets, orchestrates generation |
| **json/** | JSON serialization for model analysis output |
| **xml/** | XML file generation utilities |
| **stats/** | Generation statistics and reporting |
| **util/** | Shared utility functions |

## Documentation

- [module-sets.md](module-sets.md) - Module sets: creation, composition, and best practices
- [programmatic-content.md](programmatic-content.md) - Programmatic content modules and product XML generation
- [validation.md](validation.md) - Dependency validation system and troubleshooting
- [dependency_generation.md](dependency_generation.md) - Dependency generation pipeline architecture

## Module Set Definition

Module sets are defined in:
- `CommunityModuleSets.kt` (this module) - Community module sets
- `platform/buildScripts/src/productLayout/UltimateModuleSets.kt` - Ultimate module sets

## Product Definition

Products are defined in:
- `platform/buildScripts/src/productLayout/` - Product specifications and ultimateGenerator.kt

See [ultimateGenerator.kt](../../../../../../platform/buildScripts/src/productLayout/ultimateGenerator.kt) for the main entry point.
