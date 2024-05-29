#!/bin/sh

function usage() {
  declare -r prog="${0##*/}"
  cat <<EOF
Usage:
    $prog [-o <out_dit>] [-d <dist_dir>]
Builds MacLauncher. Binary is built in <out_dir>  (or "out" if unset).
If <dist_dir> is set, `MacLauncher` artifact is created there.
EOF
  exit 1
}

source $(dirname $0)/build-mac-common.sh

# Make
(
  cd "$out_dir"
  "$cmake_bin" -DCMAKE_BUILD_TYPE=Release "$top/tools/idea/native/MacLauncher"
  "$cmake_bin" --build .
)

verifyArchs "$out_dir/MacLauncher"

# Copy to Dist
[[ -n "${dist_dir:-}" ]] || exit 0
cp "$out_dir/MacLauncher"  "$dist_dir"
echo "Built $dist_dir/MacLauncher"