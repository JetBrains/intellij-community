# Product DSL

A Kotlin DSL for defining IntelliJ product module composition and generating module dependencies.

## How to Run

- **IDE:** Run configuration `Generate Product Layouts`
- **Bazel:** `bazel run //platform/buildScripts:plugin-model-tool`
- **CLI flags:**
  - `--json` outputs model analysis
  - `--json='{"filter":"products"}'` for specific sections

## Architecture

The generation uses a **5-stage pipeline** with slot-based `ComputeNode` execution:

```
GenerationPipeline.execute(config)
  │
  ├─ STAGE 1: DISCOVER      → Module sets + products from DSL
  ├─ STAGE 2: BUILD_MODEL   → Caches + shared values  
  ├─ STAGE 3: GENERATE      → Parallel ComputeNodes (slot-based dependencies)
  ├─ STAGE 4: AGGREGATE     → Collect errors, diffs, tracking maps
  └─ STAGE 5: OUTPUT        → Cleanup orphans, commit, build stats
```

For detailed architecture, file organization, and component documentation, see [architecture-overview.md](docs/architecture-overview.md)

## Documentation Guide

| If you want to... | Read... |
|-------------------|---------|
| Get started quickly | [quick-start.md](docs/quick-start.md) |
| Learn the DSL syntax | [dsl-api-reference.md](docs/dsl-api-reference.md) |
| Understand module sets | [module-sets.md](docs/module-sets.md) |
| Fix validation errors | [errors.md](docs/errors.md) |
| Migrate existing code | [migration-guide.md](docs/migration-guide.md) |

### All Documentation

- [quick-start.md](docs/quick-start.md) - Getting started in 5 minutes
- [dsl-api-reference.md](docs/dsl-api-reference.md) - Complete DSL function reference
- [module-sets.md](docs/module-sets.md) - Module sets: creation, composition, and best practices
- [programmatic-content.md](docs/programmatic-content.md) - Programmatic content modules, product XML generation, JSON analysis, and Ultimate-only includes
- [plugin-graph.md](docs/plugin-graph.md) - PluginGraph model and type-safe DSL for graph traversal
- [validation-rules.md](docs/validation-rules.md) - Validation rules and WHY they exist
- [errors.md](docs/errors.md) - Error messages and troubleshooting
- [dependency_generation.md](docs/dependency_generation.md) - Dependency generation pipeline architecture
- [test-plugins.md](docs/test-plugins.md) - Test plugin generation
- [migration-guide.md](docs/migration-guide.md) - Migration guides (PLATFORM_CORE_MODULES, productImplementationModules)
- [architecture-overview.md](docs/architecture-overview.md) - Detailed system architecture and file organization

## Module Set Definition

Module sets are defined in:
- `CommunityModuleSets.kt` (this module) - Community module sets
- `platform/buildScripts/src/productLayout/UltimateModuleSets.kt` - Ultimate module sets

## Product Definition

Products are defined in:
- `platform/buildScripts/src/productLayout/` - Product specifications and ultimateGenerator.kt

See [ultimateGenerator.kt](../../../../../../platform/buildScripts/src/productLayout/ultimateGenerator.kt) for the main entry point.
