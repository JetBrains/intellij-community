#!/bin/bash
set -eu

# Build Intellij native tools for Mac

function realpath() {
   cd $1 && pwd
}
# Expected arguments:
declare -r out_dir="$1"
declare -r dist_dir="$2"
declare -r build_number="$3"

declare -r script_dir=$(realpath "$(dirname "$0")")
declare exit_code=0

(
  echo "Building MacTouchBar..."
  ${script_dir}/build-mac-touchbar.sh -o ${out_dir}/MacTouchBar -d ${dist_dir} || ((exit_code++))
)

(
  echo "Building MacEnvReader..."
  ${script_dir}/build-mac-envreader.sh -o ${out_dir}/MacEnvReader -d ${dist_dir} || ((exit_code++))
)

(
  echo "Building MacLauncher..."
  ${script_dir}/build-mac-launcher.sh -o ${out_dir}/MacLauncher -d ${dist_dir} || ((exit_code++))
)

(
  echo "Building MacScreenMenu..."
  ${script_dir}/build-mac-screenmenu.sh -o ${out_dir}/MacScreenMenu -d ${dist_dir} || ((exit_code++))
)

(
  echo "Building fsNotifier..."
  ${script_dir}/build-mac-fsnotifier.sh -o ${out_dir}/fsNotifier -d ${dist_dir} || ((exit_code++))
)

(
  echo "Building xplat-launcher..."
  ${script_dir}/build-mac-xplat-launcher.sh -o ${out_dir}/xplat-launcher -d ${dist_dir} || ((exit_code++))
)


ls -lha $dist_dir

echo "All Done."
exit $exit_code