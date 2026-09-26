#!/bin/bash
# leak-loop.sh — reproduce or verify a project leak N times and archive per-run hprof+stdout.
#
# See ../SKILL.md for the surrounding fix workflow. This script:
#   1. Ensures the TeamCity-stub properties file at /tmp/tc-stub.properties exists (needed
#      so JUnit5TestSessionListener fires _LastInSuiteTest.testProjectLeak locally).
#   2. Runs `./tests.cmd --module <M> --test <FQN>` N times back-to-back.
#   3. Copies each run's `Heap dump is published to <path>.hprof.zip` file to
#      $ARCHIVE/run-<NN>.hprof.zip; saves full stdout to $ARCHIVE/run-<NN>.output.
#   4. Prints one boundary line per run so a Monitor can track progress.
#
# Fast-exit contract:
#   - If run 1 produces no `Heap dump is published to` line, the script aborts with
#     EXIT CODE 3 and does not queue the remaining runs. During Phase 2 (initial repro)
#     that is a diagnostic — leak did not reproduce. During Phase 5 (verify) that is
#     the desired positive signal that the fix worked.
#
# Usage:
#   leak-loop.sh --module <module-iml-name> --test <test-FQN> \
#                [--runs 10] [--archive /tmp/<name>-leak-runs] \
#                [--repo /path/to/intellij-repo]

set -uo pipefail

MODULE=""
TEST=""
RUNS=10
ARCHIVE=""
REPO="${REPO:-$(cd "$(dirname "$0")/../../../../.." && pwd)}"

while [ $# -gt 0 ]; do
  case "$1" in
    --module)  MODULE="$2"; shift 2 ;;
    --test)    TEST="$2";   shift 2 ;;
    --runs)    RUNS="$2";   shift 2 ;;
    --archive) ARCHIVE="$2"; shift 2 ;;
    --repo)    REPO="$2";   shift 2 ;;
    -h|--help)
      sed -n '3,22p' "$0"; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; exit 2 ;;
  esac
done

[ -n "$MODULE" ] || { echo "--module is required" >&2; exit 2; }
[ -n "$TEST"   ] || { echo "--test is required"   >&2; exit 2; }
[ -d "$REPO"   ] || { echo "--repo '$REPO' is not a directory" >&2; exit 2; }
[ -x "$REPO/tests.cmd" ] || { echo "$REPO/tests.cmd not executable" >&2; exit 2; }

if [ -z "$ARCHIVE" ]; then
  short=$(echo "$TEST" | awk -F'.' '{print tolower($NF)}')
  ARCHIVE="/tmp/${short}-leak-runs"
fi
mkdir -p "$ARCHIVE"

STUB_PROPS="/tmp/tc-stub.properties"
if [ ! -f "$STUB_PROPS" ]; then
  mkdir -p /tmp/tc-stub-cache /tmp/tc-stub-tmp
  {
    echo "agent.persistent.cache=/tmp/tc-stub-cache"
    echo "teamcity.build.tempDir=/tmp/tc-stub-tmp"
  } > "$STUB_PROPS"
  echo "Prepared TeamCity stub properties file at $STUB_PROPS"
fi

export TEAMCITY_VERSION="${TEAMCITY_VERSION:-local-leak-check}"
export TEAMCITY_BUILD_PROPERTIES_FILE="$STUB_PROPS"

cd "$REPO"

RG="$REPO/community/tools/rg.cmd"
if [ ! -x "$RG" ]; then RG=""; fi

extract_hprof() {
  local out="$1"
  if [ -n "$RG" ]; then
    "$RG" -oNI 'Heap dump is published to (\S+)' -r '$1' "$out" | head -1
  else
    grep -oE 'Heap dump is published to [^ ]+' "$out" | awk '{print $NF}' | head -1
  fi
}

echo "=== Leak-loop start: module=$MODULE test=$TEST runs=$RUNS archive=$ARCHIVE ==="

for i in $(seq 1 "$RUNS"); do
  idx=$(printf "%02d" "$i")
  OUT="$ARCHIVE/run-$idx.output"
  echo "=== Starting run $idx at $(date) ==="
  ./tests.cmd --module "$MODULE" --test "$TEST" >"$OUT" 2>&1
  rc=$?
  echo "=== Run $idx finished with exit code $rc at $(date) ==="

  HPROF=$(extract_hprof "$OUT")
  if [ -n "$HPROF" ] && [ -f "$HPROF" ]; then
    cp "$HPROF" "$ARCHIVE/run-$idx.hprof.zip"
    echo "archived hprof $(basename "$HPROF") -> run-$idx.hprof.zip"
  else
    echo "no hprof found for run $idx (rc=$rc)"
    if [ "$i" = "1" ]; then
      echo
      echo "*** Aborting loop: first run produced no leak (no 'Heap dump is published to' line)."
      echo "*** During initial repro (Phase 2): confirm --module/--test and check that"
      echo "***   ##teamcity[testStarted name=_LastInSuiteTest.testProjectLeak fired in $OUT."
      echo "*** During verify (Phase 5): this is the desired positive signal — fix works."
      exit 3
    fi
  fi
done

echo "=== All runs complete ==="
ls -la "$ARCHIVE"
