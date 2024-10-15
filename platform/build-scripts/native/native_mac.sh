#!/bin/bash
set -eu

(
  echo Xcode: $(pkgutil --pkg-info=com.apple.pkg.Xcode | awk '/version:/ {print $2}')
  echo CommandLineTools: $(pkgutil --pkg-info=com.apple.pkg.CLTools_Executables | awk '/version:/ {print $2}')
  xcode-select -p
  xcodebuild -version
  softwareupdate --history
)

# Build Intellij native tools for Mac

function realpath() {
   cd $1 && pwd
}
# Expected arguments:
declare -r out_dir="$1"
declare -r dist_dir="$2"
declare -r build_number="$3"

declare -r script_dir=$(realpath "$(dirname "$0")")
declare failed_count=0
declare failed_builds=""

echo "Building MacTouchBar..."
${script_dir}/build-mac-touchbar.sh -o ${out_dir}/MacTouchBar -d ${dist_dir} || { failed_builds+=" MacTouchBar" ;  ((failed_count++)) ; }

echo "Building MacEnvReader..."
${script_dir}/build-mac-envreader.sh -o ${out_dir}/MacEnvReader -d ${dist_dir} || { failed_builds+=" MacEnvReader" ;  ((failed_count++)) ; }

echo "Building MacScreenMenu..."
${script_dir}/build-mac-screenmenu.sh -o ${out_dir}/MacScreenMenu -d ${dist_dir} || { failed_builds+=" MacScreenMenu" ;  ((failed_count++)) ; }

echo "Building fsNotifier..."
${script_dir}/build-mac-fsnotifier.sh -o ${out_dir}/fsNotifier -d ${dist_dir} || { failed_builds+=" fsNotifier" ;  ((failed_count++)) ; }

echo "Building xplat-launcher..."
${script_dir}/build-mac-xplat-launcher.sh -o ${out_dir}/xplat-launcher -d ${dist_dir} || { failed_builds+=" launcher" ;  ((failed_count++)) ; }

echo "Building restarter..."
${script_dir}/build-mac-restarter.sh -o ${out_dir}/restarter -d ${dist_dir} || { failed_builds+=" restarter" ;  ((failed_count++)) ; }

echo "=========================="
ls -lha $dist_dir
echo "=========================="

if [ $failed_count -gt 0 ]; then
  echo "Failed to build: $failed_builds"
  exit 4
else
  echo "Done Building IntelliJ Mac Tools!"
  exit 0
fi