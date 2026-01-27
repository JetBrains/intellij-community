# JVM Incremental Compilation Benchmark

This benchmark tool compares jvm-inc-builder's incremental compilation against non-incremental mode for the same targets.

## Overview

The existing `jvm_library` rule supports both incremental and non-incremental compilation modes via threshold flags. This benchmark measures the performance difference between:

- **Incremental mode**: Default behavior with dependency graph tracking
- **Non-incremental mode**: Forces full recompilation (vanilla-like behavior)

## Quick Start

```bash
# Generate test projects
./benchmark.sh generate --files=200

# Run full benchmark suite
./benchmark.sh run --output=results.json

# Run specific scenario
./benchmark.sh run --scenario=impl_change --language=kotlin
```

## Project Structure

```
jvm-inc-builder-benchmark/
├── BUILD.bazel                              # Build targets
├── README.md                                # This file
├── benchmark.sh                             # CLI entry point
├── src/
│   ├── generator/
│   │   └── ProjectGenerator.kt              # Synthetic project generator
│   ├── runner/
│   │   ├── BenchmarkRunner.kt               # Main orchestrator
│   │   ├── BenchmarkConfig.kt               # Configuration data class
│   │   ├── BenchmarkResult.kt               # Result data structures
│   │   └── MetricsCollector.kt              # Timing/metrics collection
│   ├── scenarios/
│   │   ├── Scenario.kt                      # Scenario interface
│   │   ├── ColdBuildScenario.kt             # Initial build
│   │   ├── NoOpScenario.kt                  # No changes rebuild
│   │   ├── ImplChangeScenario.kt            # Implementation change
│   │   ├── ApiChangeScenario.kt             # API change (cascading)
│   │   └── MultiChangeScenario.kt           # Multiple file changes
│   └── output/
│       ├── JsonReporter.kt                  # JSON output
│       └── CliReporter.kt                   # Terminal summary
└── generated/                               # Generated test projects (gitignored)
```

## Benchmark Scenarios

| Scenario | Setup | Expected Behavior |
|----------|-------|-------------------|
| `cold_build` | `bazel clean` | Both modes similar (no cache) |
| `no_op` | No changes | Incremental = instant, Non-inc = full rebuild |
| `impl_change` | Modify `Impl001` body | Incremental = 1 file, Non-inc = all |
| `api_change` | Add method to `Api001` | Incremental = API + dependents, Non-inc = all |
| `multi_change` | Modify 10% of files | Incremental < Non-inc (varies by deps) |

## Usage

### Generate Test Projects

```bash
# Generate a Java-only project with 200 files
./benchmark.sh generate --files=200 --language=java

# Generate a Kotlin-only project
./benchmark.sh generate --files=200 --language=kotlin

# Generate a mixed Java/Kotlin project
./benchmark.sh generate --files=200 --language=mixed
```

### Run Benchmarks

```bash
# Run full benchmark suite
./benchmark.sh run --output=results.json

# Run with custom warmup and measurement iterations
./benchmark.sh run --warmup=5 --iterations=10

# Run specific scenario only
./benchmark.sh run --scenario=impl_change

# Verbose output
./benchmark.sh run --verbose
```

### Benchmark Real Modules

```bash
# Benchmark against real IntelliJ modules
./benchmark.sh run --target=//platform/util:util
./benchmark.sh run --target=//platform/core-api:core-api
./benchmark.sh run --target=//platform/editor-ui-api:editor-ui-api
```

### Compare Results

```bash
./benchmark.sh compare results-v1.json results-v2.json
```

## Output Format

### CLI Summary

```
=== JVM Incremental Compilation Benchmark ===

Project: java-only (200 files)
Iterations: 10 (5 warmup)

+--------------+--------------+-------------+---------+
| Scenario     | Incremental  | Non-Inc     | Speedup |
+--------------+--------------+-------------+---------+
| Cold Build   | 12.3s ±0.5s  | 12.1s ±0.4s | 0.98x   |
| No-Op        | 0.8s ±0.1s   | 11.9s ±0.3s | 14.9x   |
| Impl Change  | 1.2s ±0.2s   | 12.0s ±0.4s | 10.0x   |
| API Change   | 3.5s ±0.3s   | 12.2s ±0.5s | 3.5x    |
| Multi (10%)  | 4.8s ±0.4s   | 12.1s ±0.4s | 2.5x    |
+--------------+--------------+-------------+---------+
```

### JSON Output

```json
{
  "timestamp": "2024-01-26T10:30:00Z",
  "config": {
    "project": "java-only",
    "files": 200,
    "warmupIterations": 5,
    "measurementIterations": 10
  },
  "results": [
    {
      "scenario": "cold_build",
      "incremental": {
        "mean_ms": 12300,
        "median_ms": 12250,
        "std_dev_ms": 500,
        "min_ms": 11800,
        "max_ms": 13200
      },
      "non_incremental": { ... },
      "speedup": 0.98
    }
  ]
}
```

## How It Works

The benchmark controls incremental vs non-incremental behavior using threshold flags:

```bash
# Incremental mode (enabled)
--@rules_jvm//:koltin_inc_threshold=1
--@rules_jvm//:java_inc_threshold=1

# Non-incremental mode (disabled)
--@rules_jvm//:koltin_inc_threshold=-1
--@rules_jvm//:java_inc_threshold=-1
```

When the threshold is set to 1, incremental compilation is enabled and will recompile only changed files and their dependents. When set to -1, all files are recompiled regardless of changes.

## Generated Project Structure

Synthetic projects are generated with:

- **API classes** (20%): Public interfaces/classes
- **Implementation classes** (50%): Classes that implement APIs
- **Utility classes** (30%): Helper classes used by implementations

Dependencies flow: API → Impl ← Util

```
generated/java-benchmark/
├── BUILD.bazel
└── src/
    ├── api/
    │   ├── Api001.java
    │   └── ...
    ├── impl/
    │   ├── Impl001.java     # Depends on api/ and util/
    │   └── ...
    └── util/
        ├── Util001.java
        └── ...
```

## Verification

### Correctness
The benchmark can verify that incremental and non-incremental modes produce identical output JARs:

```kotlin
metricsCollector.verifyOutputEquivalence(target)
```

### Reproducibility
Run multiple iterations to verify low variance in measurements.

### Scaling
Test with different project sizes: 100, 250, 500 files.

## Configuration

### ProjectConfig

```kotlin
data class ProjectConfig(
    val name: String,
    val language: Language,      // JAVA, KOTLIN, MIXED
    val totalFiles: Int,         // 100, 250, 500
    val apiClassPercent: Int,    // 20% - public API classes
    val implClassPercent: Int,   // 50% - implementation classes
    val utilClassPercent: Int,   // 30% - utility classes
    val avgDepsPerClass: Int,    // 3-5 internal dependencies
)
```

### BenchmarkConfig

```kotlin
data class BenchmarkConfig(
    val projectPath: Path,
    val target: String,
    val scenarios: List<Scenario>,
    val warmupIterations: Int = 3,
    val measurementIterations: Int = 5,
    val bazelPath: String = "bazel",
    val outputPath: Path? = null,
    val verbose: Boolean = false,
)
```
