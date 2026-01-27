#!/usr/bin/env bash
# Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

# JVM Incremental Compilation Benchmark CLI
#
# This script provides a convenient interface for running benchmarks
# comparing incremental vs non-incremental JVM compilation.
#
# Usage:
#   ./benchmark.sh generate --files=200 --language=kotlin
#   ./benchmark.sh run --output=results.json
#   ./benchmark.sh run --scenario=impl_change --language=kotlin
#   ./benchmark.sh run --target=//platform/util:util
#   ./benchmark.sh compare results-v1.json results-v2.json
#   ./benchmark.sh help

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RULES_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
WORKSPACE_ROOT="$(cd "${RULES_DIR}/.." && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
  echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
  echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
  echo -e "${RED}[ERROR]${NC} $1"
}

# Check if bazel is available
check_bazel() {
  if ! command -v bazel &> /dev/null; then
    log_error "bazel not found in PATH"
    exit 1
  fi
}

# Build the benchmark tool
build_benchmark() {
  log_info "Building benchmark tool..."
  cd "${RULES_DIR}"
  bazel build //jvm-inc-builder-benchmark:benchmark
}

# Run the benchmark tool with given arguments
run_benchmark_tool() {
  cd "${RULES_DIR}"
  bazel run //jvm-inc-builder-benchmark:benchmark -- "$@"
}

# Generate test projects command
cmd_generate() {
  local files=100
  local language="java"
  local output="${SCRIPT_DIR}/generated"

  while [[ $# -gt 0 ]]; do
    case $1 in
      --files=*)
        files="${1#*=}"
        shift
        ;;
      --language=*)
        language="${1#*=}"
        shift
        ;;
      --output=*)
        output="${1#*=}"
        shift
        ;;
      *)
        log_error "Unknown option: $1"
        exit 1
        ;;
    esac
  done

  log_info "Generating ${language} project with ${files} files..."

  # Build and run the generator
  build_benchmark
  run_benchmark_tool generate --files="${files}" --language="${language}" --output="${output}"

  log_info "Projects generated in: ${output}"
}

# Run benchmark command
cmd_run() {
  local project="${SCRIPT_DIR}/generated/java-benchmark"
  local target=""
  local scenario=""
  local warmup=3
  local iterations=5
  local output=""
  local bazel="bazel"
  local verbose=""

  while [[ $# -gt 0 ]]; do
    case $1 in
      --project=*)
        project="${1#*=}"
        shift
        ;;
      --target=*)
        target="${1#*=}"
        shift
        ;;
      --scenario=*)
        scenario="${1#*=}"
        shift
        ;;
      --warmup=*)
        warmup="${1#*=}"
        shift
        ;;
      --iterations=*)
        iterations="${1#*=}"
        shift
        ;;
      --output=*)
        output="${1#*=}"
        shift
        ;;
      --bazel=*)
        bazel="${1#*=}"
        shift
        ;;
      --verbose)
        verbose="--verbose"
        shift
        ;;
      *)
        log_error "Unknown option: $1"
        exit 1
        ;;
    esac
  done

  # Default target based on project
  if [[ -z "${target}" ]]; then
    local project_name
    project_name=$(basename "${project}")
    target="//jvm-inc-builder-benchmark/generated/${project_name}:${project_name}"
  fi

  log_info "Running benchmark..."
  log_info "  Project: ${project}"
  log_info "  Target: ${target}"
  log_info "  Warmup: ${warmup}, Iterations: ${iterations}"

  # Build the benchmark tool
  build_benchmark

  # Build arguments
  local args=(run)
  args+=(--project="${project}")
  args+=(--target="${target}")
  args+=(--warmup="${warmup}")
  args+=(--iterations="${iterations}")
  args+=(--bazel="${bazel}")

  if [[ -n "${scenario}" ]]; then
    args+=(--scenario="${scenario}")
  fi

  if [[ -n "${output}" ]]; then
    args+=(--output="${output}")
  fi

  if [[ -n "${verbose}" ]]; then
    args+=(--verbose)
  fi

  run_benchmark_tool "${args[@]}"
}

# Compare results command
cmd_compare() {
  if [[ $# -lt 2 ]]; then
    log_error "Usage: benchmark.sh compare <baseline.json> <current.json>"
    exit 1
  fi

  local baseline="$1"
  local current="$2"

  if [[ ! -f "${baseline}" ]]; then
    log_error "Baseline file not found: ${baseline}"
    exit 1
  fi

  if [[ ! -f "${current}" ]]; then
    log_error "Current file not found: ${current}"
    exit 1
  fi

  build_benchmark
  run_benchmark_tool compare "${baseline}" "${current}"
}

# Help command
cmd_help() {
  cat << 'EOF'
JVM Incremental Compilation Benchmark

This tool compares jvm-inc-builder's incremental compilation against
non-incremental mode for the same targets.

USAGE:
    benchmark.sh <command> [options]

COMMANDS:
    generate    Generate synthetic test projects
    run         Run benchmark suite
    compare     Compare two benchmark results
    help        Show this help message

GENERATE OPTIONS:
    --files=N        Number of files to generate (default: 100)
    --language=LANG  Language: java, kotlin, mixed (default: java)
    --output=DIR     Output directory (default: generated)

RUN OPTIONS:
    --project=PATH   Path to project to benchmark
    --target=TARGET  Bazel target to build
    --scenario=NAME  Run specific scenario only
    --warmup=N       Warmup iterations (default: 3)
    --iterations=N   Measurement iterations (default: 5)
    --output=FILE    Output JSON file
    --bazel=PATH     Path to bazel binary (default: bazel)
    --verbose        Verbose output

COMPARE OPTIONS:
    <baseline.json> <current.json>

SCENARIOS:
    cold_build   - Initial build with no cache
    no_op        - Rebuild with no changes
    impl_change  - Single implementation file change
    api_change   - API class modification
    multi_change - Multiple file changes (10%)

REAL MODULE VALIDATION:
    You can benchmark against real IntelliJ modules:

    ./benchmark.sh run --target=//platform/util:util
    ./benchmark.sh run --target=//platform/core-api:core-api
    ./benchmark.sh run --target=//platform/editor-ui-api:editor-ui-api

EXAMPLES:
    # Generate a 200-file Kotlin project
    ./benchmark.sh generate --files=200 --language=kotlin

    # Run full benchmark suite and save results
    ./benchmark.sh run --output=results.json

    # Run only implementation change scenario
    ./benchmark.sh run --scenario=impl_change

    # Benchmark a real IntelliJ module
    ./benchmark.sh run --target=//platform/util:util

    # Compare two benchmark runs
    ./benchmark.sh compare baseline.json current.json

OUTPUT FORMAT:
    CLI output shows a table with incremental vs non-incremental times
    and speedup factors. JSON output includes detailed statistics:
    - mean, median, stddev, min, max times
    - success rates
    - per-iteration measurements

EOF
}

# Main entry point
main() {
  check_bazel

  local command="${1:-help}"
  shift || true

  case "${command}" in
    generate)
      cmd_generate "$@"
      ;;
    run)
      cmd_run "$@"
      ;;
    compare)
      cmd_compare "$@"
      ;;
    help|--help|-h)
      cmd_help
      ;;
    *)
      log_error "Unknown command: ${command}"
      cmd_help
      exit 1
      ;;
  esac
}

main "$@"
