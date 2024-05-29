#!/bin/sh

function usage() {
  declare -r prog="${0##*/}"
  cat <<EOF
Usage:
    $prog [-o <out_dit>] [-d <dist_dir>]
Builds printenv - library reading environment variables.
Binary is built in <out_dir>  (or "out" if unset).
If <dist_dir> is set, `printenv` artifact is created there.
EOF
  exit 1
}

source $(dirname $0)/build-mac-common.sh

# Make
(
  cd "$out_dir"
  "$cmake_bin" -DCMAKE_BUILD_TYPE=Release "$top/tools/idea/native/MacEnvReader"
  "$cmake_bin" --build .
)

verifyArchs "$out_dir/printenv"

# Copy to Dist
[[ -n "${dist_dir:-}" ]] || exit 0
cp "$out_dir/printenv"  "$dist_dir"
echo "Built $dist_dir/printenv"