#!/bin/bash

set -eux

export RUN_WITHOUT_ULTIMATE_ROOT=true

script_dir="$(cd "$(dirname "$0")"; pwd)"

cd "$script_dir/.."
output_base=$(./bazel.cmd info output_base)
echo "Bazel output base: $output_base"

./build/jpsModelToBazelCommunityOnly.cmd
./bazel-build-all-community.cmd

cd "$script_dir/../platform/build-scripts/bazel"
exec /bin/bash "../../../bazel.cmd" run "$@" //:jps-to-bazel -- \
  "--assert-all-library-roots-exist-with-output-base=$output_base" \
  --assert-all-module-outputs-exist
