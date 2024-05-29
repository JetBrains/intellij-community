#!/bin/sh

function usage() {
  declare -r prog="${0##*/}"
  cat <<EOF
Usage:
    $prog [-o <out_dit>] [-d <dist_dir>]
Builds XPlatLauncher for mac. Binary is built in <out_dir>  (or "out" if unset).
If <dist_dir> is set, `XPlatLauncher` artifact is created there.
EOF
  exit 1
}

source $(dirname $0)/build-mac-common.sh

echo "Building Mac XPlatLauncher......."
echo "out_dir=${out_dir:-}"
echo "dist_dir=${dist_dir:-}"
echo "rust_dir=${rust_dir:-}"
echo "top=${top:-}"

(
  cd "$top/tools/idea/native/XPlatLauncher"
  export RUSTC="$rust_dir/bin/rustc"
  "$rust_dir/bin/cargo" build --verbose  --locked --release --target x86_64-apple-darwin --target-dir "$out_dir"
  "$rust_dir/bin/cargo" build --verbose  --locked --release --target aarch64-apple-darwin --target-dir "$out_dir"
  lipo -create -output "$out_dir/xplat-launcher" "$out_dir/aarch64-apple-darwin/release/xplat-launcher" "$out_dir/x86_64-apple-darwin/release/xplat-launcher"
)

verifyArchs "$out_dir/xplat-launcher"

# Copy to Dist
[[ -n "${dist_dir:-}" ]] || exit 0
cp "$out_dir/x86_64-apple-darwin/release/xplat-launcher"  "$dist_dir"
echo "Built $dist_dir/xplat-launcher"