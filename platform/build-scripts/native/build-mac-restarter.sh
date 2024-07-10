#!/bin/sh

function usage() {
  declare -r prog="${0##*/}"
  cat <<EOF
Usage:
    $prog [-o <out_dit>] [-d <dist_dir>]
Builds Restarter for mac. Binary is built in <out_dir>  (or "out" if unset).
If <dist_dir> is set, `restarter` artifact is created there.
EOF
  exit 1
}

source $(dirname $0)/build-mac-common.sh

echo "Building Mac Restarter ......."
echo "out_dir=${out_dir:-}"
echo "dist_dir=${dist_dir:-}"
echo "rust_dir=${rust_dir:-}"
echo "top=${top:-}"

(
  cd "$top/tools/idea/native/restarter"
  export RUSTC="$rust_dir/bin/rustc"
  "$rust_dir/bin/cargo" build --verbose  --locked --release --target x86_64-apple-darwin --target-dir "$out_dir"
  "$rust_dir/bin/cargo" build --verbose  --locked --release --target aarch64-apple-darwin --target-dir "$out_dir"
  lipo -create -output "$out_dir/restarter" "$out_dir/aarch64-apple-darwin/release/restarter" "$out_dir/x86_64-apple-darwin/release/restarter"
)

verifyArchs "$out_dir/restarter"

# Copy to Dist
[[ -n "${dist_dir:-}" ]] || exit 0
cp "$out_dir/restarter" "$dist_dir"
echo "Built $dist_dir/restarter"