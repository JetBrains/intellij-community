# JVM Incremental Compilation Benchmark

Benchmark tool comparing jvm-inc-builder's incremental compilation against non-incremental mode.

## Prerequisites

- Bazel installed and available in PATH
- Working directory: `build/jvm-rules/`

## Quick Start

Run the benchmark in 3 steps:

```bash
cd build/jvm-rules

# 1. Generate a test project (100 Java files)
bazel run //jvm-inc-builder-benchmark:benchmark -- generate \
  --files=100 \
  --language=java \
  --output=$(pwd)/jvm-inc-builder-benchmark/generated

# 2. Run the benchmark
bazel run //jvm-inc-builder-benchmark:benchmark -- run \
  --project=$(pwd)/jvm-inc-builder-benchmark/generated/java-benchmark \
  --target=//jvm-inc-builder-benchmark/generated/java-benchmark:java-benchmark \
  --warmup=2 \
  --iterations=3

# 3. (Optional) Save results to JSON
bazel run //jvm-inc-builder-benchmark:benchmark -- run \
  --project=$(pwd)/jvm-inc-builder-benchmark/generated/java-benchmark \
  --target=//jvm-inc-builder-benchmark/generated/java-benchmark:java-benchmark \
  --output=results.json
```

## Example Output

```
=== JVM Incremental Compilation Benchmark ===

Project: java-benchmark (100 files, java)
Iterations: 3 (2 warmup)

+--------------+--------------+--------------+---------+
| Scenario     | Incremental  | Non-Inc      | Speedup |
+--------------+--------------+--------------+---------+
| cold build   | 3.1s ±0.0s   | 3.2s ±0.0s   | 1.03x   |
| no op        | 174ms ±6ms   | 1.1s ±0.0s   | 6.62x   |
| impl change  | 400ms ±21ms  | 1.3s ±0.0s   | 3.48x   |
| api change   | 277ms ±28ms  | 1.2s ±0.0s   | 4.38x   |
| multi chang  | 379ms ±29ms  | 1.5s ±0.2s   | 3.98x   |
+--------------+--------------+--------------+---------+

Average speedup: 3.90x
```

## Commands

### Generate Test Projects

```bash
# Java project (default)
bazel run //jvm-inc-builder-benchmark:benchmark -- generate \
  --files=100 --language=java --output=$(pwd)/jvm-inc-builder-benchmark/generated

# Kotlin project
bazel run //jvm-inc-builder-benchmark:benchmark -- generate \
  --files=100 --language=kotlin --output=$(pwd)/jvm-inc-builder-benchmark/generated

# Mixed Java/Kotlin project
bazel run //jvm-inc-builder-benchmark:benchmark -- generate \
  --files=100 --language=mixed --output=$(pwd)/jvm-inc-builder-benchmark/generated
```

### Run Benchmarks

```bash
# Full benchmark suite
bazel run //jvm-inc-builder-benchmark:benchmark -- run \
  --project=<path-to-project> \
  --target=<bazel-target>

# Single scenario only
bazel run //jvm-inc-builder-benchmark:benchmark -- run \
  --project=<path-to-project> \
  --target=<bazel-target> \
  --scenario=no_op

# With verbose output
bazel run //jvm-inc-builder-benchmark:benchmark -- run \
  --project=<path-to-project> \
  --target=<bazel-target> \
  --verbose

# Custom iterations
bazel run //jvm-inc-builder-benchmark:benchmark -- run \
  --project=<path-to-project> \
  --target=<bazel-target> \
  --warmup=5 \
  --iterations=10
```

### Available Options

| Option | Description | Default |
|--------|-------------|---------|
| `--project=PATH` | Path to project directory | required |
| `--target=TARGET` | Bazel target to build | required |
| `--scenario=NAME` | Run specific scenario only | all |
| `--warmup=N` | Warmup iterations | 3 |
| `--iterations=N` | Measurement iterations | 5 |
| `--output=FILE` | Output JSON file | none |
| `--bazel=PATH` | Path to bazel binary | bazel |
| `--verbose` | Verbose output | false |

### Show Help

```bash
bazel run //jvm-inc-builder-benchmark:benchmark -- help
```

## Benchmark Scenarios

| Scenario | Description | Expected Result |
|----------|-------------|-----------------|
| `cold_build` | Build after `bazel clean` | Both modes similar (~1x) |
| `no_op` | Rebuild with no changes | Incremental much faster (5-15x) |
| `impl_change` | Modify implementation file body | Incremental faster (3-10x) |
| `api_change` | Add method to API class | Incremental faster (3-5x) |
| `multi_change` | Modify 10% of files | Incremental faster (2-4x) |

## How It Works

The benchmark controls incremental vs non-incremental behavior using threshold flags:

```bash
# Incremental mode (enabled) - recompiles only changed files
--@rules_jvm//:koltin_inc_threshold=1
--@rules_jvm//:java_inc_threshold=1

# Non-incremental mode (disabled) - always full rebuild
--@rules_jvm//:koltin_inc_threshold=-1
--@rules_jvm//:java_inc_threshold=-1
```

Each scenario:
1. Sets up the test state (modify files, clean cache, etc.)
2. Runs incremental build and measures time
3. Resets state
4. Runs non-incremental build and measures time
5. Reports the speedup ratio

## Generated Project Structure

Projects are generated with a realistic dependency graph:

```
generated/java-benchmark/
├── BUILD.bazel
└── src/
    ├── api/          # 20% - Public interfaces
    │   ├── Api001.java
    │   └── ...
    ├── impl/         # 50% - Implementations (depend on api/ and util/)
    │   ├── Impl001.java
    │   └── ...
    └── util/         # 30% - Utilities (used by impl/)
        ├── Util001.java
        └── ...
```

## JSON Output Format

```json
{
  "timestamp": "2024-01-26T10:30:00Z",
  "config": {
    "project": "java-benchmark",
    "files": 100,
    "language": "java",
    "warmupIterations": 3,
    "measurementIterations": 5
  },
  "results": [
    {
      "scenario": "no_op",
      "incremental": {
        "mean_ms": 174,
        "median_ms": 170,
        "std_dev_ms": 6,
        "min_ms": 170,
        "max_ms": 184,
        "success_rate": 1.0
      },
      "non_incremental": {
        "mean_ms": 1152,
        "median_ms": 1144,
        "std_dev_ms": 12,
        "min_ms": 1136,
        "max_ms": 1164,
        "success_rate": 1.0
      },
      "speedup": 6.62
    }
  ]
}
```

## Tips

1. **Warm up first**: Use at least 2-3 warmup iterations for stable results
2. **Multiple measurements**: Use 5+ measurement iterations to reduce noise
3. **Larger projects**: Test with 200-500 files to see more pronounced differences
4. **Verbose mode**: Use `--verbose` to see individual build commands and times
5. **Compare runs**: Save JSON output and compare across code changes

## Troubleshooting

**Build fails with "target not found"**
- Ensure the generated project path is absolute
- Check that the target matches the project name in BUILD.bazel

**High variance in results**
- Increase warmup iterations
- Close other applications
- Run on a quiet machine

**API change scenario shows build failures**
- This is expected - adding interface methods breaks implementors
- The timing is still captured correctly
